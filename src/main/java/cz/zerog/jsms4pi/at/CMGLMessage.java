package cz.zerog.jsms4pi.at;

/*
 * #%L
 * jSMS4Pi
 * %%
 * Copyright (C) 2015 - 2018 jSMS4Pi
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

import static cz.zerog.jsms4pi.tool.PatternTool.CR_LF;
import static cz.zerog.jsms4pi.tool.PatternTool.NUMBER;
import static cz.zerog.jsms4pi.tool.PatternTool.PHONE_NUMBER;
import static cz.zerog.jsms4pi.tool.PatternTool.STAT;
import static cz.zerog.jsms4pi.tool.PatternTool.TIME_STAMP;
import static cz.zerog.jsms4pi.tool.PatternTool.WHATEVER;
import static cz.zerog.jsms4pi.tool.PatternTool.build;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CMGLMessage {
	private static final Pattern pattern = Pattern
			.compile(build("\\+CMGL: *({}),\"({})\",\"({})\",({}),\"({})\"{}({}){}", NUMBER, STAT, // stat
					PHONE_NUMBER, // oa
					WHATEVER, // alpha
					TIME_STAMP, // scts
					CR_LF, WHATEVER, // data
					CR_LF));

	private final int index;

	public CMGLMessage(Matcher matcher) {
		index = Integer.parseInt(matcher.group(1));
	}

	public int getSMSIndex() {
		return index;
	}

	public static List<CMGLMessage> tryParse(String notification) {
		ArrayList<CMGLMessage> cmglList = new ArrayList<CMGLMessage>();
		Matcher matcher = pattern.matcher(notification);
		while (matcher.find()) {
			cmglList.add(new CMGLMessage(matcher));
		}
		return cmglList;
	}

}
