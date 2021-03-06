/*
 *  Copyright (C) 2004-2020 Savoir-faire Linux Inc.
 *
 *  Author: Alexandre Lision <alexandre.lision@savoirfairelinux.com>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package cx.ring.model;

public class Phone {

    // TODO: make sure this is usefull since a Uri should already know this
    private NumberType mNumberType;

    private Uri mNumber;
    private int mCategory; // Home, work, custom etc.
    private String mLabel;


    public Phone(Uri number, int category) {
        mNumberType = NumberType.UNKNOWN;
        mNumber = number;
        mLabel = null;
        mCategory = category;
    }

    public Phone(String number, int category) {
        this(number, category, null);
    }

    public Phone(String number, int category, String label) {
        mNumberType = NumberType.UNKNOWN;
        mCategory = category;
        mNumber = new Uri(number);
        mLabel = label;
    }
    public Phone(Uri number, int category, String label) {
        mNumberType = NumberType.UNKNOWN;
        mCategory = category;
        mNumber = number;
        mLabel = label;
    }

    public Phone(String number, int category, String label, NumberType numberType) {
        mNumberType = numberType;
        mNumber = new Uri(number);
        mLabel = label;
        mCategory = category;
    }
    public Phone(Uri number, int category, String label, NumberType numberType) {
        mNumberType = numberType;
        mNumber = number;
        mLabel = label;
        mCategory = category;
    }

    public NumberType getType() {
        return getNumbertype();
    }

    public void setType(int type) {
        setNumberType(NumberType.fromInteger(type));
    }

    public Uri getNumber() {
        return mNumber;
    }

    public void setNumber(String number) {
        setNumber(new Uri(number));
    }

    public NumberType getNumbertype() {
        return mNumberType;
    }

    public void setNumber(Uri number) {
        this.mNumber = number;
    }

    public int getCategory() {
        return mCategory;
    }

    public String getLabel() {
        return mLabel;
    }

    public void setNumberType(NumberType numberType) {
        this.mNumberType = numberType;
    }

    public enum NumberType {
        UNKNOWN(0),
        TEL(1),
        SIP(2),
        IP(2),
        RING(3);

        private final int type;

        NumberType(int t) {
            type = t;
        }

        private static final NumberType[] VALS = NumberType.values();

        public static NumberType fromInteger(int id) {
            for (NumberType type : VALS) {
                if (type.type == id) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }
}
