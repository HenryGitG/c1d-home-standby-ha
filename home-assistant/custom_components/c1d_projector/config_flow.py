"""Config flow for the C1D Projector integration."""

from __future__ import annotations

import voluptuous as vol

from homeassistant import config_entries
from homeassistant.config_entries import ConfigFlowResult
from homeassistant.const import CONF_HOST, CONF_PORT, CONF_TOKEN
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.service_info.zeroconf import ZeroconfServiceInfo

from .client import C1DAuthError, C1DClient, C1DClientError
from .const import CONF_DEVICE_ID, DEFAULT_NAME, DOMAIN


class C1DProjectorConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """Handle C1D Projector discovery and authentication."""

    VERSION = 1

    def __init__(self) -> None:
        self._host = ""
        self._port = 0
        self._device_id = ""

    async def async_step_user(self, user_input=None) -> ConfigFlowResult:
        """Require discovery so no fixed address is ever stored by the user."""
        return self.async_abort(reason="discovery_required")

    async def async_step_zeroconf(
        self, discovery_info: ZeroconfServiceInfo
    ) -> ConfigFlowResult:
        """Handle a discovered projector and refresh an existing address."""
        device_id = discovery_info.properties.get("id")
        if not device_id:
            return self.async_abort(reason="invalid_discovery")

        self._host = discovery_info.host
        self._port = discovery_info.port
        self._device_id = device_id
        await self.async_set_unique_id(device_id)
        self._abort_if_unique_id_configured(
            updates={CONF_HOST: self._host, CONF_PORT: self._port}
        )
        self.context["title_placeholders"] = {"name": DEFAULT_NAME}
        return await self.async_step_zeroconf_confirm()

    async def async_step_zeroconf_confirm(self, user_input=None) -> ConfigFlowResult:
        """Ask once for the private API token."""
        errors = {}
        if user_input is not None:
            token = user_input[CONF_TOKEN].removeprefix("Bearer ").strip()
            client = C1DClient(
                async_get_clientsession(self.hass), self._host, self._port, token
            )
            try:
                await client.async_state()
            except C1DAuthError:
                errors["base"] = "invalid_auth"
            except C1DClientError:
                errors["base"] = "cannot_connect"
            else:
                return self.async_create_entry(
                    title=DEFAULT_NAME,
                    data={
                        CONF_HOST: self._host,
                        CONF_PORT: self._port,
                        CONF_TOKEN: token,
                        CONF_DEVICE_ID: self._device_id,
                    },
                )

        return self.async_show_form(
            step_id="zeroconf_confirm",
            data_schema=vol.Schema({vol.Required(CONF_TOKEN): str}),
            errors=errors,
            description_placeholders={"name": DEFAULT_NAME},
        )
