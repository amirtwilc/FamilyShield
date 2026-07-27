# History section

## Goal

Explain the purpose of the History section and how it looks.


## Language

Although this spec describes the components in English, components and text should be translated to all supported languages in the app.

## Section name

On the bottom menu the section is called History, but the title of the section is History & Routes

## Switch between children

Below the title there is a switch option between children, similar to the one in the Dashboard section (but without the "All Children" or the "Add child" options).
If a child was chosen in another section, for example the Dashboard, then upon entering this section this child be be dispalyed by default.

## Frequent routes

Contain all the recurring (more than once) routes (explained later) for a child.
Contains the top 5 (most ocurred) routes. On tie, choose recently uses.
Frequent route is decided by it's start and end points. This means that the actual route might be inherently different in terms of how the child actually moved, but as long as the start and end points are the same then it will still count as the same route.
A route is clickable, affecting the Daily timeline component.
If no frequent routes have been detected, then a message will show "Once <child name> travels the same path a few times, it'll appear hear".

Clicking a route will affect the Days switcher by greying out (making them not clickable) the days that do not contain the clicked route. It will also highlight the most recent day the route was made. 
Example: Today is the 20th and the 19th is now clicked and shown. A route is now clicked that has data from the 8th, 9th and 10th.
The Days switcher will move (becuase the 10th is more than 7 days old) until the 10th is visible, the 10th will be highlighted, and the data for the 10th will be shown instead of the 19th. The Days switcher can still be swiped to see other days (the same that were shown before, according to the data the app has), but all the days that are not the 8th, 9th and 10th will be greyed out, so cannot be clicked.
Once a route was chosen and a day was highlighted (either automatically or chosen), the timeline data below the Days switcher will only show the routes made one that day (could be one or more), so any activity that is not described as the chosen route should not be visible at all.

Clicking a route again will remove the route from the display, thus returning the screen to the original behavior where all days are clickable and all activities are shown. If because of choosing a route the day was changed (for example the 10th), then unchoosing the route will still keep that day has the chosen day.

Although routes are described as being differnt when going from A to B and B to A, for this component these routes should be named the same, meaning that if a child went from Home to School and from School to Home, they should be displayed as one route, and when clicked the shown routes should be either from Home to School or from School to Home. In the name of the route, use a double arrow instead of a regular arrow, so it is understood that it means both ways.
Notice: Going from A to Z and then from Z to A does not make this a frequent (recurring) route. A recurring route should still mean that either route A to Z or Z to A was performed more than once.

## Daily timeline

### Days switcher

A row of days, showcasing the current day and past days. Each day is clickable, affecting the timeline shown below the days switcher.
Days are displayed with the day of the week (should be translated between languages) and the date (only the day, without the month)
For LTR language, current day is on the far right, and for RTL language current day is on the far left.
The row should accomodate 7 days at all times, so each day box size should be achieve that. 
If there is data for 7 or less days, the row should display the last 7 days.
If there is data for more than 7 days, the row should be swipable so more days are visible. Need to make sure the swipe does not interefer with swiping between sections. Swiping between sections should be possible when swiping anywhere EXCEPT the days switcher.
Displayed days might be different between children. They both will display the last days, but if child A has data for 10 days and child B has only 5 days, then only for child A it would be able to go back 10 days back.
If for example there is child data for 10 days ago, but no data 8 days ago, day switcher will still show 10 days back.
The assumption is that child location data will not be saved forever or for a long time, so app should be able to accomodate this behavior.

### Timeline

This part of the section is meant to show a child location activity through the day.
If a picked day does not have data, a message will be displayed "No location updates recorded on this day".
Location activity is sorted from newest to oldest, so most recent activity is first.
Each activity is seperated and contains: The time, Location name, coordinates, View on Map button.

#### The time

Time will be displayed at 24 hours format.
Time may be a specific hour or a range of hours. 
If adjacent location data are from the same location, then a range should be used, thus combining the same location in one box. Very slight deviations in coordinates should be considered as noise, thus still accounted for the same location.
If a route (explained later) has been spotted, then a range should be used that demonstrates the time the route started and ended.

#### Location name

If coordiantes are inside a Zone, that zone name should be picked as the name.
If coordinates are not inside a Zone, there should be an effort to give that location a name from the map.
If activity is a route, name should include the start and end point (inside zone - zone name, not inside a zone - retrieve from map), with an arrow in the middle.

#### Coordiantes

display the coordinates as-is

#### View on Map button

Clicking the button should open a map view shocasing the activity.
If it is a single point, point should be centered and pointed to.
If it is a route, the end point should be centered on and there should be a line connecting all the different points from start to finish.

## Routes

A route is represented when data suggests that a child is moving, thus not staying in the same spot for a long time.
The calculation should be based on time and not adjacent data, because if retrieval time is very often (every few seconds) it might not be clear that child is moving. Or if child has paused for a minute and continued, it should still count as a continous route.
Use 5 minutes as the base of the calculation, so if a child started a route, waited somewhere for 4 minutes and then continued, it will count as a continous route. If the wait was more than 5 minutes and then moved, then divide it to a different route. 5 minutes should be a parameter that should be easily changed later.
If calculation suggests child has been moving, it should be described as route. 
The start point of the route should normally be the previous spot that child was resting (more than 5 minutes), and the end point should normally be the new point where child was resting (more than 5 minutes). For example: Child was Home (zone) from 10:00 to 11:00, then calculation noticed a movement until 12:00, where child was noticed to be on the same spot (Let's call it A street, which is not a zone) from 13:00 until 14:00. This means that the route start point is Home at 12:00 and end point is A street at 13:00.

---

## Before planning

Assume the requirements are not thought out completely, and some gaps might be present. It is your job to mediate these gaps before starting to code.
Make sure you understand the task completely. Ask questions to clarify ambigious or untouched areas.
Feel free to give suggestions for things that were not considered and give your opinion if you believe things should be handled differently.

## Done When

- All parts were implemented
- All parts were tested throughly
