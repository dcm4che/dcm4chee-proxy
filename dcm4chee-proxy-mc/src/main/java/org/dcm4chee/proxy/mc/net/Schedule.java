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
import java.util.Calendar;
import java.util.Date;
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

    private GregorianCalendar startCal;
    private GregorianCalendar endCal;

    public long getForwardTime() {
        Date now = new GregorianCalendar().getTime();
        if ( !isNowBetweenDate( now, startCal.getTime(), endCal.getTime() ) )
            return startCal.getTimeInMillis();
        return 0;
    }

    public Schedule(String dayOfWeek, String hour) {

        if ( dayOfWeek != null ) {
            String[] days = dayOfWeek.split("-");
            if (days.length == 2) {
                startCal = setCalFromDay(days[0], startCal);
                endCal = setCalFromDay(days[1], endCal);
            } else {
                LOG.error("Wrong format for dayOfWeek: " + dayOfWeek);
            }
        }

        if ( hour != null ) {
            String[] hours = hour.split("-");
            if (hours.length == 2) {
                startCal = setCalFromHour(hours[0], startCal);
                endCal = setCalFromHour(hours[1], endCal);
            } else {
                LOG.error("Wrong format for hour: " + hour);
            }
        }
    }

    private GregorianCalendar setCalFromHour(final String hh, GregorianCalendar gc) {
        if (hh.matches("^[0-2][0-9]$"))
            gc.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hh));
        return gc;
    }

    private GregorianCalendar setCalFromDay(final String day, GregorianCalendar gc) {
        final List<String> days = Arrays.asList("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat");
        if(days.contains(day))
            gc.set(Calendar.DAY_OF_WEEK, days.indexOf(day));
        return gc;
    }
    
    boolean isNowBetweenDate(Date now, Date start, Date end) {
        if (start.after(end))
            return now.after(start) || now.before(end);
        return now.after(start) && now.before(end);
    }
}
