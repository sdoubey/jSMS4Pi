package cz.zerog.jsms4pi.exception;

import com.pi4j.io.serial.SerialPortException;

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

/**
 *
 * @author zerog
 */
@SuppressWarnings("serial")
public class GatewayException extends Exception {

	public static final String SERVISE_READ_ERR = "Cannot read the message servis number";
	public static final String RESPONSE_EXPIRED = "The response timeout expired";
	public static final String GATEWAY_CLOSED = "The gateway is closed";
	public static final String GATEWAY_NOT_READY = "The gateway is not ready. A current state: ";
	public static final String SERIAL_ERROR = "The serial port refuse parameters";
	public static final String CANNOT_SEND = "Cannot send the text message";

	public GatewayException(SerialPortException cause) {
		super(cause);
	}

	public GatewayException(String errorMessage, String port) {
		super(errorMessage + ". Port: " + port);
	}

	public GatewayException(CmsError error, String port) {
		this("", error, port);
	}

	public GatewayException(String message, CmsError error, String port) {
		this(message + ". " + cmsToString(error), port);
	}

	private static String cmsToString(CmsError cmsError) {
		if (cmsError == null) {
			return "";
		}
		return "CMS Error: " + cmsError.getNumber() + ", '" + cmsError.getText() + "'";
	}
}
