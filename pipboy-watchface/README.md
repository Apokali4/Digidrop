# Pip-Boy Watch Face

A resource-only Watch Face Format 1 watch face for Wear OS 4 (API 33+).

## Build

The `feature/pipboy-watchface` branch builds the watch-face APK in GitHub Actions. Download the `PipBoy-Watchface-APK` artifact from the workflow run.

## Live data

- System time and date
- System step count
- Watch battery percentage
- Two user-configurable complication slots; choose compatible weather or other data providers in the watch-face editor

## Ambient mode

The decorative active-mode frame and the stats/complication areas are hidden in ambient mode. Time and date remain visible.

## Platform limits

This is a Watch Face Format 1 resource-only face. It does not implement a custom notification inbox or message-reply UI. Notifications remain handled by Wear OS. Complication content depends on the provider selected by the user and may be empty when no compatible provider is configured.

## Install

Install the debug APK on the paired Wear OS watch, then select **Pip-Boy 3000** from the watch-face picker. Configure the two complication slots from the watch-face customization screen.
