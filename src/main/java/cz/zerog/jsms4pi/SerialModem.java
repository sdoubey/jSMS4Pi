package cz.zerog.jsms4pi;

import java.io.IOException;

/*
 * #%L
 * jSMS4Pi
 * %%
 * Copyright (C) 2015 jSMS4Pi
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.io.gpio.exception.UnsupportedBoardType;
import com.pi4j.io.serial.Baud;
import com.pi4j.io.serial.DataBits;
import com.pi4j.io.serial.Parity;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialConfig;
import com.pi4j.io.serial.SerialDataEvent;
import com.pi4j.io.serial.SerialDataEventListener;
import com.pi4j.io.serial.SerialFactory;
import com.pi4j.io.serial.SerialPort;
import com.pi4j.io.serial.SerialPortException;
import com.pi4j.io.serial.StopBits;

import cz.zerog.jsms4pi.at.AAT;
import cz.zerog.jsms4pi.exception.GatewayException;
import cz.zerog.jsms4pi.notification.CDS;
import cz.zerog.jsms4pi.notification.CDSI;
import cz.zerog.jsms4pi.notification.CLIPN;
import cz.zerog.jsms4pi.notification.CMT;
import cz.zerog.jsms4pi.notification.CMTI;
import cz.zerog.jsms4pi.notification.CREG;
import cz.zerog.jsms4pi.notification.Notification;
import cz.zerog.jsms4pi.notification.RING;
import cz.zerog.jsms4pi.notification.UnknownNotifications;

/**
 *
 * @author zerog
 */
public class SerialModem implements Runnable, Modem, SerialDataEventListener, Thread.UncaughtExceptionHandler {

	// Logger
	private static final Logger log = LoggerFactory.getLogger(SerialModem.class);

	/*
	 * RS-232 setting
	 */
	private String portName;
	protected Serial serialPort;
	private final DataBits DATA_BIT;
	private final StopBits STOP_BIT;
	private final Parity PARITY;
	private final Baud BOUDRATE;
	private final int AT_TIMEOUT; // ms

	/*
	 * Gateway
	 */
	private volatile Gateway gateway;

	/*
	 * Object which waiting for part of response Notification or AT
	 */
	private ATResponse atResponse = null;

	private final BlockingQueue<Notification> notificationQueue = new LinkedBlockingQueue<>();

	/*
	 * Status of modem
	 */
	private Mode mode = Mode.NOT_INIT;

	/*
	 * State of notification thread
	 */
	private volatile NotifyState notifyState = NotifyState.READY;

	/*
	 * This thread notify gateway
	 */
	private Thread notifyThread;

	public SerialModem(String portName, Baud boudrate, DataBits databit, StopBits stopbit, Parity parity,
			int atTimeout) {
		this.BOUDRATE = boudrate;
		this.AT_TIMEOUT = atTimeout;
		this.DATA_BIT = databit;
		this.STOP_BIT = stopbit;
		this.PARITY = parity;
		this.portName = portName;

		this.serialPort = SerialFactory.createInstance();

		log.info("SerialModem start (constructor)");
	}

	public SerialModem(String portName, Baud boudrate) {
		this(portName, boudrate, DataBits._8, StopBits._1, Parity.NONE, 30 * 1000);
	}

	public SerialModem(String portName, Baud boudrate, int atResponseTO) {
		this(portName, boudrate, DataBits._8, StopBits._1, Parity.NONE, atResponseTO);
	}

	public SerialModem(String portName) {
		this(portName, Baud._57600);
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		try {
			this.close();
			log.error("Modem was closed by uncaught exception.");
		} catch (GatewayException e1) {
			log.error("SerialModem", e1);
		}
		log.error("Uncaght Exception in Thread: " + t.getName(), e);
	}

	@Override
	public void open() throws GatewayException {
		if (mode.equals(Mode.NOT_INIT)) {
			throw new IllegalStateException("Gateway is not set. Use a setGateway method first");
		}

		try {
			close();

			SerialConfig config = new SerialConfig();
			config.device(portName).baud(BOUDRATE).dataBits(DATA_BIT).stopBits(STOP_BIT).parity(PARITY);

			serialPort.addListener(this);
			serialPort.open(config);

			// set and start thread
			notifyThread = new Thread(this);
			notifyThread.setName("SerialNotifyThread");
			notifyThread.setDaemon(true);
			notifyThread.setUncaughtExceptionHandler(this);
			notifyThread.start();

			mode = Mode.READY;
			log.info("Port opened '{}'", portName);
		} catch (SerialPortException | IOException ex) {
			throw new GatewayException(new SerialPortException(ex));
		}
	}

	@Override
	public void close() throws GatewayException {
		if (serialPort != null && serialPort.isOpen()) {
			try {
				serialPort.removeListener(this);
				serialPort.discardAll();
				serialPort.close();
				mode = Mode.NOT_OPEN;
			} catch (IOException ex) {
				throw new GatewayException(new SerialPortException(ex));
			}
		}
		if (notifyThread != null) {
			notifyThread.interrupt();
		}
	}

	@Override
	public <T extends AAT> T send(T cmd) throws GatewayException {
		if (!mode.equals(Mode.READY)) {
			throw new GatewayException(GatewayException.GATEWAY_NOT_READY + mode, portName);
		}

		atResponse = cmd;
		notifyState = NotifyState.AT;

		cmd.setWaitingStatus();
		String request = cmd.getPrefix() + cmd.getRequest();
		log.info("Request: {}", AAT.crrt(request));
		try {
			serialPort.write(request);
			synchronized (cmd) {
				cmd.wait(AT_TIMEOUT);
			}

			if (cmd.isStatus(AAT.Status.WAITING)) {
				GatewayException e = new GatewayException(GatewayException.RESPONSE_EXPIRED, portName);
				log.warn("SerialModem", e);
				throw e;
			}
		} catch (InterruptedException ex) {
			log.warn("Interuppted, while wait for answer");
		} catch (IOException ex) {
			throw new GatewayException(new SerialPortException(ex));
		}
		atResponse = null;
		notifyState = NotifyState.READY;
		return cmd;
	}

	@Override
	public void run() {
		try {
			while (true) {
				gateway.notify(notificationQueue.take());
				log.info("new notification --> Gateway");
			}
		} catch (InterruptedException e) {
			// end of thread
		}
	}

	@Override
	public void dataReceived(SerialDataEvent event) {
		// if received some bytes
		try {
			if (event.length() > 0) {

				// read it
				String response = event.getAsciiString();

				// log.info("NATIVE: [{}]", crrt(response));

				switch (notifyState) {
				case AT:
					synchronized (atResponse) {
						if (((AAT) atResponse).appendResponse(response)) {
							log.info("Response: [{}]", AAT.crrt(atResponse.getResponse()));
							atResponse.notify();
						}
					}

					break;
				case READY:
					atResponse = new UnknownNotifications();
				case NOTIFY:
					notifyState = NotifyState.NOTIFY;

					/*
					 * If "response" is complete
					 */
					atResponse.appendResponse(response);

					List<Notification> internalQueue = new ArrayList<>();
					while (((UnknownNotifications) atResponse).hasNextMessage()) {

						String notificationMessage = ((UnknownNotifications) atResponse).getNextMessage();

						Notification notification = findNotification(notificationMessage,
								(UnknownNotifications) atResponse);

						if (notification == null) {
							log.info("Detected unknow notification: [{}]", AAT.crrt(notificationMessage));
							continue;
						}

						log.info("Detected notification: [{}]", AAT.crrt(notification.getResponse()));
						internalQueue.add(notification);
					}

					if (((UnknownNotifications) atResponse).isEmpty()) {
						notifyState = NotifyState.READY;
						for (Notification n : internalQueue) {
							notificationQueue.put(n);
							log.info("Added notification {}", n.getResponse());
						}
						internalQueue.clear();
					}
					break;
				default:
					log.warn("Unexcepted message '{}' in state '{}'.", response, mode);
					break;
				}

			}
		} catch (IOException | InterruptedException e) {
			log.warn("SerialModem", e);
		}
	}

	@Override
	public int getSpeed() {
		return BOUDRATE.getValue();
	}

	@Override
	public int getAtTimeout() {
		return AT_TIMEOUT;
	}

	@Override
	public String getPortName() {
		return portName;
	}

	@Override
	public void setGatewayListener(Gateway gateway) {
		if (gateway == null) {
			throw new NullPointerException("Gateway cannot by null");
		}
		if (this.gateway != null) {
			throw new IllegalStateException("Gateway is already set");
		}
		this.gateway = gateway;
		mode = Mode.NOT_OPEN;
	}

	@Override
	public void putNotification(Notification notification) {
		try {
			notificationQueue.put(notification);
		} catch (InterruptedException e) {
			log.error("SerialModem", e);
		}
	}

	public static String[] getAvailablePorts() {
		try {
			return new String[] { SerialPort.getDefaultPort() };
		} catch (UnsupportedBoardType | IOException | InterruptedException e) {
			return new String[0];
		}
	}

	private Notification findNotification(String notificationMessage, UnknownNotifications notifications) {
		Notification notification;

		if ((notification = RING.tryParse(notificationMessage)) != null) {
			return notification;
		}

		if ((notification = CLIPN.tryParse(notificationMessage)) != null) {
			return notification;
		}

		if ((notification = CDSI.tryParse(notificationMessage)) != null) {
			return notification;
		}

		if ((notification = CMTI.tryParse(notificationMessage)) != null) {
			return notification;
		}

		if ((notification = CMT.tryParse(notificationMessage, notifications)) != null) {
			return notification;
		}

		if ((notification = CDS.tryParse(notificationMessage, notifications)) != null) {
			return notification;
		}

		if ((notification = CREG.tryParse(notificationMessage)) != null) {
			return notification;
		}
		return null;
	}

	private enum NotifyState {
		READY, AT, NOTIFY;
	}

}
