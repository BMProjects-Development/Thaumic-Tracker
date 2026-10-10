# Thaumic Tracker: tracking your research

[Русский](guide-ru.md) · [Home](../README.md)

Choose an entry in the Thaumonomicon and follow its requirements while
gathering items or working at your base.

- [Installation](#installation)
- [Your first tracked entry](#your-first-tracked-entry)
- [Reading the panel](#reading-the-panel)
- [Controls](#controls)
- [Switching and stopping tracking](#switching-and-stopping-tracking)
- [Saving your selection](#saving-your-selection)
- [Custom research](#custom-research)
- [Troubleshooting](#troubleshooting)

## Installation

Version **1.0** requires:

| Component | Version |
| --- | --- |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.109 or later within 26.1.2 |
| Java | 25 |
| Thaumaturge | 1.0.2, with its required dependencies |

1. Download `thaumictracker-26.1.2-1.0.jar` from
   [Releases](https://github.com/BMProjects-Development/Thaumic-Tracker/releases).
2. Put it in your Minecraft instance's `mods` folder.
3. Start the client with Thaumaturge and its dependencies installed.

Install the regular JAR. The `-sources.jar` contains source code for developers.
The release ZIP contains the game JAR in `mods/` and these guides; do not put
that ZIP in the `mods` folder.

In multiplayer, install Thaumic Tracker on each client that wants the panel.
The server only needs Thaumaturge and its normal dependencies. KubeThaum is
optional. Other Thaumaturge versions need a compatibility check before use.

## Your first tracked entry

1. Join a world or server and open the Thaumonomicon.
2. Open the research entry you want to follow.
3. Find the small pin ribbon at the **bottom edge of the left page**.
   Hover over it to see **Track research**.
4. Click the ribbon and close the book.

A compact panel appears on the right with the research title, current stage
and requirements. The selected entry's ribbon changes appearance. Selecting
an entry also shows the panel if you had hidden it with its hotkey.

For example, an entry needs four iron ingots, a theory note and completed
prerequisite research. The panel shows all three requirements. With two
ingots in your inventory, the item row reads `2 / 4`. After you collect four,
that row gains a green check mark.

## Reading the panel

The panel refreshes every ten client ticks, about twice per second during
normal play. Green check marks indicate requirements that are currently met.

| Requirement | What the tracker checks |
| --- | --- |
| Items | Matching items in your inventory, including item tags and required components. The count is capped at the required amount. |
| Crafting | Thaumaturge's own crafting check, including stored craft history. This is a satisfied/unsatisfied check, not a separate craft counter. |
| Research | Whether prerequisite research is complete. |
| Theory | Whether the required theory note has been learned. Carrying the note alone is insufficient. |
| Observation | Whether the aspect is discovered and enough points are available in its aspect pool. Undiscovered aspect names remain hidden. |

When all requirements are met, **open the Thaumonomicon and advance the stage
there**. The tracker does not submit items or complete research for you.
It then displays the next stage automatically. Completed research remains
selected with a completion message until you switch entries or stop tracking.

Removing required items from your inventory can clear their check marks on
the next refresh. The panel reflects your current state.

## Controls

| Default key | Action |
| --- | --- |
| **J** | Show or hide the panel without clearing the selection. |
| **]** | Show the next page of a long requirement list. |

Change either binding in **Options → Controls → Key Binds → Thaumic Tracker**.
Use the keys while playing with menus closed. The page indicator appears
only when there are multiple pages and shows the configured page key.
A new research stage returns the list to its first page.

The panel hides while screens are open, the F3 overlay is shown, or the HUD
is hidden with F1. Very small GUI areas may leave no room for the panel:
lower Minecraft's GUI scale or enlarge the window. Version 1.0 has no separate
tracker size or position setting.

## Switching and stopping tracking

To switch entries, open a different entry and click its ribbon. Only one
entry is tracked; the new selection replaces the previous one.

To clear the selection, open the selected entry and click its ribbon again.
Its tooltip now says **Stop tracking**. To hide the panel temporarily and keep
the selection, use **J** instead.

## Saving your selection

Selection is saved separately for each world/server and player account in
`config/thaumictracker.json`. Rejoining restores it. World paths and server
addresses are hashed for the stored selection keys. Moving a single-player
world to a different folder or using a different server address can create
a separate selection.

Panel visibility is global. Press **J** or select an entry again to show a
hidden panel. To reset all preferences, close Minecraft, back up
`config/thaumictracker.json`, and remove it. The file is recreated when
preferences are next saved.

## Custom research

Datapack and KubeThaum research uses the same native Thaumaturge registry.
Open a custom entry and pin it like a built-in entry. No tracker scripts or
additional configuration are needed.

For example, a pack author can add an entry requiring planks and a completed
prerequisite. Once the pack has loaded the entry, players can select it and
follow both requirements on the panel.

The tracker follows current Thaumaturge data and progress. It does not change
when custom research loads or how stages work. If a pack update removes a
selected entry, select another existing entry.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| No pin ribbon | Open an entry detail page, not the category overview. Confirm Thaumaturge 1.0.2 and Thaumic Tracker are installed on the client. |
| No panel | Select an entry, close screens, press J and hide F3. Check key conflicts and GUI scale. |
| A requirement seems satisfied but is unchecked | Compare the book. Item components, learned notes and aspect-pool points matter; a similar item or an unlearned note may not qualify. |
| All requirements met but the stage has not changed | Advance the stage in the Thaumonomicon. Submission is not automatic. |
| A title appears as a translation key | Install the custom entry's client translations or fix its language data. |
| Saved selection is missing | Check whether the account, world folder or server address changed. Select the entry again. |

For other problems, [open an issue](https://github.com/BMProjects-Development/Thaumic-Tracker/issues)
with versions, steps to reproduce, a screenshot if useful, and relevant lines
from `logs/latest.log`.
