/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.proxy.mc.net;

import java.util.BitSet;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.dcm4che.util.StringUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class Schedule {

    private static final String[] DAYS = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
    private enum ToInt {
        parseHour {

            @Override
            public int begin(String s, String value) {
                try {
                    int index = Integer.parseInt(s);
                    if (index >= 0 && index < 24)
                        return index;
                } catch (NumberFormatException e) {
                }
                throw new IllegalArgumentException(value);
            }

            @Override
            public int end(String s, String value) {
                return begin(s, value) + 1;
            }

            @Override
            public int size() {
                return 24;
            }},
        parseDay {

                @Override
                public int begin(String s, String value) {
                    for (int i = 0; i < DAYS.length; i++)
                        if (s.equalsIgnoreCase(DAYS[i]))
                            return i;
                    throw new IllegalArgumentException(value);
                }

                @Override
                public int end(String s, String value) {
                    return begin(s, value);
                }

            @Override
            public int size() {
                return 7;
            }
        };
        abstract int begin(String s, String value);
        abstract int end(String s, String value);
        abstract int size();
    }

    private final BitSet days = new BitSet(7);
    private final BitSet hours = new BitSet(24);

    public Schedule() {
        days.set(0, 7);
        hours.set(0, 24);
    }

    public void setDays(String dayOfWeek) {
        set(days, dayOfWeek, ToInt.parseDay);
    }
    
    public void setHours(String hour) {
        set(hours, hour, ToInt.parseHour);
    }
    
    public boolean sendNow(){
        final Calendar now = new GregorianCalendar();
        return days.get(now.get(Calendar.DAY_OF_WEEK)) 
                && hours.get(now.get(Calendar.HOUR_OF_DAY)-1);
    }

    private void set(BitSet bs, String value, ToInt ti) {
        for (String s : StringUtils.split(value, ','))
            set(bs, StringUtils.split(s, '-'), value, ti);
    }

    private void set(BitSet bs, String[] range, String value, ToInt ti) {
        switch (range.length) {
        case 1:
            bs.set(ti.begin(range[0], value));
            break;
        case 2:
            for (int i = ti.begin(range[0], value), end = ti.begin(range[1], value);
                i != end ; i = (i + 1) % ti.size())
                    bs.set(i);
            break;
        default:
            throw new IllegalArgumentException(value);
        }
        
    }

}
