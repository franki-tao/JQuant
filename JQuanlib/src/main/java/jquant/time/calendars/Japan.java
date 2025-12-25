package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.JapanImpl;

///! Japanese calendar
/**! Holidays:
    <ul>
    <li>Saturdays</li>
    <li>Sundays</li>
    <li>New Year's Day, January 1st</li>
    <li>Bank Holiday, January 2nd</li>
    <li>Bank Holiday, January 3rd</li>
    <li>Coming of Age Day, 2nd Monday in January</li>
    <li>National Foundation Day, February 11th</li>
    <li>Emperor's Birthday, February 23rd since 2020 and December 23rd before</li>
    <li>Vernal Equinox</li>
    <li>Greenery Day, April 29th</li>
    <li>Constitution Memorial Day, May 3rd</li>
    <li>Holiday for a Nation, May 4th</li>
    <li>Children's Day, May 5th</li>
    <li>Marine Day, 3rd Monday in July</li>
    <li>Mountain Day, August 11th (from 2016 onwards)</li>
    <li>Respect for the Aged Day, 3rd Monday in September</li>
    <li>Autumnal Equinox</li>
    <li>Health and Sports Day, 2nd Monday in October</li>
    <li>National Culture Day, November 3rd</li>
    <li>Labor Thanksgiving Day, November 23rd</li>
    <li>Bank Holiday, December 31st</li>
    <li>a few one-shot holidays</li>
    </ul>
    Holidays falling on a Sunday are observed on the Monday following
    except for the bank holidays associated with the new year.

    \ingroup calendars
*/
public class Japan extends Calendar {
    public Japan() {
        super();
        impl_ = new JapanImpl();
    }
}
