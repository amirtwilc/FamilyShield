# Parent side UI UX

This spec refers to the Android app, specifically about the user interface of the parent, after a sign-in was established successfully

## Goal

Illustrate all sections of the parent interface regarding appearance, functionality and user experience.
Most of the current layout should be removed entirely and rewritten from scratch according to this spec, so if an existing section is not mentioned in the spec - it should be removed.
The current app feel/design/font should remain the same.
Although this spec is in English, The app should support Hebrew as well. Unless explicitly mentioned, all text must be translated to Hebrew.

## Layout

The following bottom and upper layout should be consistent and visible in all sections.
The direction of text and buttons should be aligned according the chosen language of the app, whether it's LTR or RTL language

### LTR (English)

Upper bar: 
Left side: [shield icon] [FamilyShield text] .... Right side: [Settings button]
**note**: Remove the bell icon from upper bar

Bottom toolbar:
From left to right: Dashboard->Chat->Map->History->Zones

### RTL (Hebrew)

Upper bar: 
Right side: [shield icon] [FamilyShield text in English] .... Left side: [Settings button]
**note**: Remove the bell icon from upper bar

Bottom toolbar:
From right to left: בית->צ'אט->מפה->היסטוריה->אזורים

## Sections

### Dashboard

If no child is paired to parent, a text saying "No child was added" and then a button saying "Add child", which will allow to add a child name (same as in Setting screen).
If at least one child was added, then a separate box will be show for each added child. A box will contain the following information, in this order:
- Status: Active (in green - means child is paired and a location was fetched at least 10 minutes ago) / Not paired (in red - means child is not paired) / No signal (in red - means child is paired, but last signal was over 2 hours ago) / Not accurate (in yellow - means child is paired, but last location was retrieved over 15 minutes ago, but less than 2 hours)
- Child name
- 
