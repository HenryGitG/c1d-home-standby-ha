"""Switch entity for C1D network standby."""

from __future__ import annotations

from homeassistant.components.switch import SwitchEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .client import C1DClient, C1DClientError
from .const import CONF_DEVICE_ID, DEFAULT_NAME, DOMAIN


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    """Set up the projector switch."""
    async_add_entities([C1DProjectorSwitch(entry, entry.runtime_data)], True)


class C1DProjectorSwitch(SwitchEntity):
    """Represent the projector as on or network standby."""

    _attr_has_entity_name = True
    _attr_name = None
    _attr_icon = "mdi:projector"

    def __init__(self, entry: ConfigEntry, client: C1DClient) -> None:
        self._client = client
        self._attr_unique_id = entry.data[CONF_DEVICE_ID]
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, entry.data[CONF_DEVICE_ID])},
            name=DEFAULT_NAME,
            manufacturer="XGIMI / OBE",
            model="C1D",
        )

    async def async_update(self) -> None:
        """Read current panel state."""
        try:
            self._attr_is_on = await self._client.async_state()
            self._attr_available = True
        except C1DClientError:
            self._attr_available = False

    async def async_turn_on(self, **kwargs) -> None:
        """Wake the projector."""
        await self._async_set_state(True)

    async def async_turn_off(self, **kwargs) -> None:
        """Enter network standby."""
        await self._async_set_state(False)

    async def _async_set_state(self, on: bool) -> None:
        try:
            self._attr_is_on = await self._client.async_set_state(on)
            self._attr_available = True
            self.async_write_ha_state()
        except C1DClientError as error:
            self._attr_available = False
            self.async_write_ha_state()
            raise HomeAssistantError("C1D projector did not respond") from error
