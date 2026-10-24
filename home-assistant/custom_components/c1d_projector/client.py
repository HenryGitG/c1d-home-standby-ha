"""Small HTTP client for the C1D network-standby API."""

from __future__ import annotations

import asyncio

from aiohttp import ClientError, ClientSession, ClientTimeout


class C1DClientError(Exception):
    """Base client error."""


class C1DAuthError(C1DClientError):
    """Authentication failed."""


class C1DClient:
    """Talk to one C1D projector."""

    def __init__(self, session: ClientSession, host: str, port: int, token: str) -> None:
        self._session = session
        self._host = host
        self._port = port
        self._token = token.removeprefix("Bearer ").strip()

    @property
    def url(self) -> str:
        return f"http://{self._host}:{self._port}/api/power"

    async def async_state(self) -> bool:
        data = await self._async_request("GET")
        return data.get("state") == "on"

    async def async_set_state(self, on: bool) -> bool:
        data = await self._async_request(
            "POST", json={"state": "on" if on else "standby"}
        )
        return data.get("state") == "on"

    async def _async_request(self, method: str, **kwargs) -> dict:
        try:
            async with self._session.request(
                method,
                self.url,
                headers={"Authorization": f"Bearer {self._token}"},
                timeout=ClientTimeout(total=5),
                **kwargs,
            ) as response:
                if response.status == 401:
                    raise C1DAuthError
                response.raise_for_status()
                return await response.json()
        except C1DAuthError:
            raise
        except (asyncio.TimeoutError, ClientError, ValueError) as error:
            raise C1DClientError from error
