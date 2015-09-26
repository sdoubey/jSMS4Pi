package cz.zerog.jsms4pi.at;

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
 * Returns the make, model and capabilities of the phones.
 *
 * @author zerog
 */
public class CGMM extends AAT {

    public static final String NAME = "+CGMM";

    private String modelInfo;

    public CGMM() {
        super(NAME);
    }

    @Override
    protected void parseCommandResult(String response) {
        modelInfo = AAT.deleteCrrt(response);
    }

    public String getModelInfo() {
        return modelInfo;
    }
}