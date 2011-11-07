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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class Schedule {

    private static final Logger LOG =
        LoggerFactory.getLogger(Schedule.class);

    private BitSet days = new BitSet(7);
    private BitSet hours = new BitSet(24);

    public long getForwardTime() {
        final Calendar now = new GregorianCalendar();
        return days.get(now.get(Calendar.DAY_OF_WEEK)) 
                ? hours.get(now.get(Calendar.HOUR_OF_DAY)) 
                        ? 0 
                        : getNextDate(now) 
                : getNextDate(now);
    }

    private long getNextDate(Calendar now) {
        final GregorianCalendar cal = new GregorianCalendar();
        
        int nextDay = days.nextSetBit(now.get(Calendar.DAY_OF_WEEK));
        if ( nextDay == -1 )
            cal.set(Calendar.DAY_OF_WEEK, days.nextSetBit(0));
        else
            cal.set(Calendar.DAY_OF_WEEK, nextDay);
        
        int nextHour = days.nextSetBit(now.get(Calendar.HOUR_OF_DAY));
        if ( nextHour == -1 )
            cal.set(Calendar.HOUR_OF_DAY, hours.nextSetBit(0));
        else
            cal.set(Calendar.HOUR_OF_DAY, nextHour);
        
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        
        return cal.getTimeInMillis();
    }

    public Schedule(String dayOfWeek, String hour) {
        
        String[] schedule;
        
        if ( dayOfWeek != null ) {
            schedule = dayOfWeek.split("-");
            if (schedule.length == 2) {
                final List<String> dl = Arrays.asList("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat");
                int j = dl.indexOf(schedule[1])+1;
                for ( int i = dl.indexOf(schedule[0])+1; i % 7 != j; i++)
                    days.set( i % 7 );
                days.set(j);
            } else {
                LOG.error("Wrong format for dayOfWeek: " + dayOfWeek);
            }
        }
        
        if ( hour != null ) {
            schedule = hour.split("-");
            if (schedule.length == 2) {
                int j = Integer.parseInt(schedule[1]);
                for ( int i = Integer.parseInt(schedule[0]); i % 24 != j; i++)
                    hours.set( i % 24 );
            } else {
                LOG.error("Wrong format for hour: " + hour);
            }
        }
    }
}
