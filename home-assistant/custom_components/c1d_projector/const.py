"""Constants for the C1D Projector integration."""

from homeassistant.const import Platform

DOMAIN = "c1d_projector"
PLATFORMS = [Platform.SWITCH]

CONF_DEVICE_ID = "device_id"
DEFAULT_NAME = "C1D 投影仪"
DEFAULT_PATH = "/api/power"
