# Thaumic Tracker

A client-side research tracker for **Thaumaturge 1.0.2**, **Minecraft 26.1.2** and **NeoForge 26.1.2.109+** (within 26.1.2).

Open a research entry in the Thaumonomicon and click **Track research** in the upper-right corner. After closing the book, the HUD displays the requirements of its current stage. Only one research entry is tracked at a time; selecting another replaces it. Click **Stop tracking** on the selected entry to remove it.

- **J** shows or hides the panel.
- **]** switches pages when a stage has too many requirements to fit.
- Both keys can be changed in Minecraft's Controls menu, under **Thaumic Tracker**.
- Inventory counts, prerequisites, craft history, learned theory notes and observation aspect points update twice per second.
- The panel follows stage changes and displays completion. Continue research in the Thaumonomicon when the requirements are met.
- Research from datapacks and KubeThaum uses the same native registry and is supported without additional configuration. KubeThaum is optional.
- Selection is remembered separately for each world/server and player account. Visibility is saved globally in `config/thaumictracker.json`.
- English and Russian translations are included. The HUD is hidden with F1, the debug overlay and open screens.

## Installation

Install the regular `thaumictracker-26.1.2-1.0.jar` in the **client's** `mods` folder alongside Thaumaturge 1.0.2 and its required dependencies. Do not install the `-sources.jar` as a mod. The server needs Thaumaturge; it does not need Thaumic Tracker.

This release targets the published Thaumaturge 1.0.2 file (CurseForge file **9095192**). The dependency is intentionally restricted to 1.0.2 because the book integration accesses an internal screen field. Future Thaumaturge releases need a compatibility check.

No new items, blocks, textures, models or sounds are added. The panel uses text, rectangles and existing item icons. No dependency is bundled in the release JAR, and the tracker does not send packets, advance research or consume resources.

## Building and verification

Use **JDK 25** and the included Gradle wrapper:

```sh
./gradlew build
./gradlew runGameTestServer -PsmokeTests
./gradlew runClientSmoke -PsmokeTests
```

On Windows, use `gradlew.bat`. Set `JAVA_HOME` to a JDK 25 installation if the default Java is older. Release JARs are written to `build/libs/`.

The first command runs persistence tests. The GameTest checks requirements against a real Minecraft player, inventory and Thaumaturge registries. The client smoke test creates a disposable world, checks the actual book button, resize behavior, hotkey and server-to-client stage synchronization, saves screenshots and exits. It opens a Minecraft window. Test worlds and screenshots are confined to `run-gametest/` and `run-smoke-client/`. Test code and sample research are excluded from release JARs.

## По-русски

**Thaumic Tracker** показывает требования выбранного исследования Thaumaturge прямо на игровом экране.

Откройте страницу исследования в таумономиконе и нажмите **«Отслеживать»** в правом верхнем углу. Закройте книгу — сбоку появится список требований текущего этапа. Можно отслеживать одно исследование; выбор другого заменяет предыдущее. Кнопка **«Не отслеживать»** убирает выбранное исследование.

**J** скрывает и показывает панель. **]** переключает страницы длинного списка. Обе клавиши можно переназначить в настройках управления, в разделе **Thaumic Tracker**.

Трекер учитывает предметы в инвентаре, историю крафта, необходимые исследования, изученные записки теории и запас очков аспектов для наблюдений. По мере прохождения он показывает новый этап и завершение исследования. Когда требования выполнены, продолжите исследование в книге.

Выбор сохраняется отдельно для каждого мира/сервера и аккаунта. Поддерживаются обычные исследования из датапаков и KubeThaum; устанавливать KubeThaum для работы трекера не требуется.

Установите обычный JAR в папку `mods` **клиента**. Нужны Minecraft **26.1.2**, NeoForge **26.1.2.109 или новее в ветке 26.1.2**, Thaumaturge **1.0.2** и его обязательные зависимости. На сервере достаточно Thaumaturge.
