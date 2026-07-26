# Safe zone section

## Goal

Explain the purpose of the Zones section, what components it includes and how they connect to the entire app.


## Language

Although this spec describes the components in English, components and text should be translated to all supported languages in the app.

## Zones are shared across all children

Zones are shared across all children, meaning that if a zone was created around child A, then child B will also use this zone.
A zone may not be used by one child and not the other.

## Section Layout

Title

Explanation

All created zones by the user, displayed one by one, last created zone first

Plus button to add a new zone

### Title

Title of the section is: Safe Zones & Alerts

### Explanation

Manage automated notifications when children enters or leaves an area

### Zone list

Each zone in the list will display it's name, if it's active/not active, the radius, edit button, delete button and a small map display showing the zone on the map.

### Plus button

Appears at the bottom right corner, always visible, no matter if scrolling the page. 

## Creating a zone

When pressing the Plus button, a new zone can be created around a child location. 
The creation is performed via a floating window.
Creation requires the following information:
- Name (e.g. School)
- Radius: A bar will allow to select a range between 50 and 2000 meters. Default at 50m. Moving the bar jumps in 10m (so can't choose 15m). A plus and minus button appear at each side of the bar, respectively, so user may pick a value more precisely by jumping 10m each press. 
- Centered around child current location: Will allow to change around which child to set the zone around. It will show a default, currently active, child, but will allow to change the child by a drop-down option. Only children with known location will be available.

## Editing a zone

Editing a zone should allow to change a zone name and radius, but not the location (around which child). It can also change a zone from active to not active (and vice versa)

## Removing a zone

Clicking the remove button should display an "Are you sure?" prompt. 
Clicking Yes should remove the zone completely from screen and database.

## Notifications

The main goal of zones is to send notifications to parents when ever a child enters a (active) zone or when they exit it.
This information should also be displayed under the Recent Alert component in the Dashboard section.