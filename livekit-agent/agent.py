import asyncio
import logging
import os

import httpx
from dotenv import load_dotenv
from livekit import rtc
from livekit.agents import AutoSubscribe, JobContext, WorkerOptions, cli, stt
from livekit.plugins import aws

load_dotenv()
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger("stt-agent")

BACKEND_URL = os.environ["BACKEND_URL"]
INTERNAL_TOKEN = os.environ["INTERNAL_STT_TOKEN"]


async def transcribe_track(
    room_name: str,
    participant_identity: str,
    audio_track: rtc.Track,
) -> None:
    stt_engine = aws.STT(language="ko-KR")
    audio_stream = rtc.AudioStream(audio_track)
    stt_stream = stt_engine.stream()

    async def push_frames() -> None:
        async for frame_event in audio_stream:
            stt_stream.push_frame(frame_event.frame)
        await stt_stream.aclose()

    push_task = asyncio.create_task(push_frames())

    async with httpx.AsyncClient() as http:
        async for event in stt_stream:
            if event.type != stt.SpeechEventType.FINAL_TRANSCRIPT:
                continue
            text = event.alternatives[0].text.strip()
            if not text:
                continue
            logger.info("[%s] %s: %s", room_name, participant_identity, text)
            try:
                await http.post(
                    f"{BACKEND_URL}/api/internal/stt",
                    json={
                        "roomId": room_name,
                        "participantIdentity": participant_identity,
                        "text": text,
                    },
                    headers={"X-Internal-Token": INTERNAL_TOKEN},
                    timeout=5.0,
                )
            except Exception as exc:
                logger.error("백엔드 전송 실패: %s", exc)

    await push_task


async def entrypoint(ctx: JobContext) -> None:
    await ctx.connect(auto_subscribe=AutoSubscribe.AUDIO_ONLY)
    logger.info("방 입장: %s", ctx.room.name)

    tasks: set[asyncio.Task] = set()

    def spawn_transcription(track: rtc.Track, participant: rtc.RemoteParticipant) -> None:
        if track.kind != rtc.TrackKind.KIND_AUDIO:
            return
        task = asyncio.create_task(
            transcribe_track(ctx.room.name, participant.identity, track)
        )
        tasks.add(task)
        task.add_done_callback(tasks.discard)

    @ctx.room.on("track_subscribed")
    def on_track_subscribed(
        track: rtc.Track,
        _pub: rtc.RemoteTrackPublication,
        participant: rtc.RemoteParticipant,
    ) -> None:
        spawn_transcription(track, participant)

    for participant in ctx.room.remote_participants.values():
        for pub in participant.track_publications.values():
            if pub.track:
                spawn_transcription(pub.track, participant)

    disconnect_future: asyncio.Future = asyncio.get_event_loop().create_future()

    @ctx.room.on("disconnected")
    def on_disconnected(*_: object) -> None:
        if not disconnect_future.done():
            disconnect_future.set_result(None)

    await disconnect_future

    for task in list(tasks):
        task.cancel()


if __name__ == "__main__":
    cli.run_app(
        WorkerOptions(
            entrypoint_fnc=entrypoint,
            agent_name="stt-agent",
        )
    )