package dev.felnull.imp.server.music.ringer;

import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.advancements.IMPCriteriaTriggers;
import dev.felnull.imp.music.tracker.MusicTrackerEntry;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.server.webdav.ServerWebDAVProxyManager;
import org.modsauce.otyacraftenginerenewed.advancement.ModInvolvementTrigger;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class MusicRing {

  /**
   * How many consecutive "cannot load" reports from one listener trigger the server-side fallback (skip next / stop);
   * one is enough, the client reports only after its own load attempt (probes and address checks) already failed.
   */
  private static final int MAX_LOAD_FAIL = 1;
  /** How many tracks one ringer may skip in a row through that fallback; on reaching it the ringer stops instead of skipping forever. */
  private static final int MAX_RING_SKIP = 3;

  private final ServerLevel level;
  private final Map<UUID, IMusicRinger> ringers = new HashMap<>();
  private final Map<UUID, RingedPlayerInfos> playerInfos = new HashMap<>();
  private final Set<UUID> waitRingers = new HashSet<>();

  /**
   * [Server-side fallback] Consecutive "cannot load" reports per listener (ringerUUID → playerId → count). Must not be
   * shared: one player's failures would otherwise skip the track or stop the ringer for everyone. Server thread only.
   */
  private final Map<UUID, Map<UUID, Integer>> loadFailCounts = new HashMap<>();

  /**
   * [Server-side fallback] Tracks skipped in a row per ringer, kept here rather than in {@link RingedPlayerInfos} — that instance is recreated (losing its counts) whenever the ring is rebuilt, so the cap could never be reached.
   */
  private final Map<UUID, Integer> ringSkippedCounts = new HashMap<>();

  private long baseTime;
  private long pauseTime = -1;
  private long lastTime;
  /**
   * [Diagnostic] Last logged "Ring tick" state per ringer, capped, so an unchanged tick repeats nothing. {@code pos} is deliberately excluded from the comparison (it changes every tick) but is still printed.
   */
  private static final Map<UUID, String> RING_TICK_LAST_STATE = new HashMap<>();
  /** Same-key cap: a long session would otherwise pile up keys; 64 is far above the real ringer count. */
  private static final int RING_TICK_MAX_KEYS = 64;
  private static final org.apache.logging.log4j.Logger LOGGER =
    org.apache.logging.log4j.LogManager.getLogger(MusicRing.class);

  public MusicRing(ServerLevel level) {
    this.level = level;
    this.baseTime = System.currentTimeMillis();
    this.lastTime = getTime();
  }

  private ServerLevel getLevel() {
    return level;
  }

  protected void tick() {
      var stopRingers = new ArrayList<UUID>();
  
      long startTime = getTime();
      LOGGER.debug(
        "MusicRing.tick start - lastTime: {}, startTime: {}, ringers: {}",
        lastTime,
        startTime,
        ringers.size()
      );
  
      for (IMusicRinger ringer : ringers.values()) {
        var uuid = ringer.getRingerUUID();

        // [Trace, log only] The three branches below plus stopRingers at the end of this tick are the only
        // sources of a STOP sent to the client.
        if (
          dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled(
            "ring:" + uuid
          )
        ) {
          boolean ex = ringer.exists();
          boolean sameLevel = ringer.getRingerLevel() == this.level;
          var dbgSrc = ex ? ringer.getRingerMusicSource() : null;
          var dbgSrcStr = dbgSrc == null
            ? "null"
            : dbgSrc.getLoaderType() + ":" + dbgSrc.getIdentifier();
          long dbgSrcDur = dbgSrc == null ? -1L : dbgSrc.getDuration();
          boolean dbgPlaying = ex && ringer.isRingerPlaying();
          boolean dbgRpi = playerInfos.containsKey(uuid);
          boolean dbgLoop = ex && ringer.isRingerLoop();
          boolean dbgRemote = ex && ringer.isRingerRemote();
          // [Diagnostic] Print only when this ringer's tick state changes: the same state lines repeated every
          // tick; the last logged state (all fields except pos) is per ringerUUID and compared before logging.
          var dbgState =
            dbgSrcStr +
            "|" +
            dbgSrcDur +
            "|" +
            dbgPlaying +
            "|" +
            dbgRpi +
            "|" +
            dbgLoop +
            "|" +
            dbgRemote +
            "|" +
            ex +
            "|" +
            sameLevel;
          if (!dbgState.equals(RING_TICK_LAST_STATE.get(uuid))) {
            if (RING_TICK_LAST_STATE.size() > RING_TICK_MAX_KEYS)
              RING_TICK_LAST_STATE.clear();
            RING_TICK_LAST_STATE.put(uuid, dbgState);
            dev.felnull.imp.client.music.link.LinkDebug.log(
              "Ring tick",
              "uuid",
              uuid,
              "exists",
              ex,
              "sameLevel",
              sameLevel,
              "src",
              dbgSrcStr,
              "srcDur",
              dbgSrcDur,
              "playing",
              dbgPlaying,
              "rpi",
              dbgRpi,
              "pos",
              ex ? ringer.getRingerPosition() : -1L,
              "loop",
              dbgLoop,
              "remote",
              dbgRemote
            );
          }
        }

        // Stop the ringer if it no longer exists OR has changed dimensions
        if (!ringer.exists() || ringer.getRingerLevel() != this.level) {
          stopRingers.add(uuid);
        } else {
          var ms = ringer.getRingerMusicSource();
          if (ms == null || !ringer.isRingerPlaying()) {
            if (
              (ms != null || playerInfos.containsKey(uuid)) &&
              dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled(
                "ringstop:" + uuid
              )
            ) {
              var dbgInfos = playerInfos.get(uuid);
              dev.felnull.imp.client.music.link.LinkDebug.log(
                "ring stop branch",
                "uuid",
                uuid,
                "src",
                ms == null ? "null" : ms.getLoaderType() + ":" + ms.getIdentifier(),
                "playing",
                ringer.isRingerPlaying(),
                "hadInfos",
                dbgInfos != null,
                "listen",
                dbgInfos == null ? -1 : dbgInfos.listenPlayers.size(),
                "middle",
                dbgInfos == null ? -1 : dbgInfos.middleLoadPlayers.size(),
                "firstReady",
                dbgInfos == null ? -1 : dbgInfos.firstReadyPlayers.size(),
                "firstWait",
                dbgInfos == null ? -1 : dbgInfos.firstWaitPlayers.size()
              );
            }
            ringer.setRingerPlaying(false);
            stopRingingPlayer(uuid);
          } else {
            var rpi = playerInfos.get(uuid);
            if (rpi == null) {
              rpi = new RingedPlayerInfos(uuid, startTime);
              rpi.sendFirstPackets();
              playerInfos.put(uuid, rpi);
              waitRingers.add(uuid);
              if (
                dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled(
                  "ringcreate:" + uuid
                )
              ) dev.felnull.imp.client.music.link.LinkDebug.log(
                "ring create infos",
                "uuid",
                uuid,
                "firstWait",
                rpi.firstWaitPlayers.size()
              );
              LOGGER.debug(
                "Added RingedPlayerInfos for {} at time {}",
                uuid,
                startTime
              );
            }
  
            if (rpi.tick(startTime)) {
              waitRingers.remove(uuid);
              // The tick-interval base, not "now": `lastTime` is this tick's own start, so summing `eq`
              // reproduces the wall clock exactly (sampling it here and stamping the end of the tick dropped
              // the tail of every tick — the ringer clock ran ~0.2-3 % slow, so the bottom HUD lyric drifted behind).
              long currentTime = startTime;
              long eq = currentTime - lastTime;
              long prevPos = ringer.getRingerPosition();
              boolean isStream = ringer.isRingerStream();
              long duration = ms != null ? ms.getDuration() : 0;
              LOGGER.debug(
                "Updating ringer {}: prevPos={}, duration={}, isStream={}, lastTime={}, currentTime={}, eq={}",
                uuid,
                prevPos,
                duration,
                isStream,
                lastTime,
                currentTime,
                eq
              );
  
              if (duration >= prevPos + eq || isStream) {
                var sc = ringer.getRingerMusicSource();
                long dur = sc != null ? sc.getDuration() : 0;
                long newPos = clamp(
                  prevPos + eq,
                  0,
                  dur
                );
                // [Skip] The client already dropped the sponsor/ad seconds on the read side, so the "now playing"
                // position has to land past the segment. The timeline stays on **source time** (the stored
                // RingerPosition, the HUD/lyrics and a late joiner's start all use it), so this only moves the
                // position forward — the end of the track arrives that much earlier without computing a content
                // length. The segment table comes from the client.
                if (sc != null && !isStream) {
                  newPos = Math.min(dur, dev.felnull.imp.server.music.BiliBiliSponsorCache.getInstance()
                    .positionAfterSkips(sc.getIdentifier(), newPos));
                }
                ringer.setRingerPosition(newPos);
                LOGGER.debug("Set ringer {} position -> {}", uuid, newPos);
              } else {
                ringer.setRingerPosition(0);
                ringer.ringerEnd();
                LOGGER.debug(
                  "Ringer {} reached end; looping? {}",
                  uuid,
                  ringer.isRingerLoop()
                );
                if (ringer.isRingerLoop()) {
                  stopRingingPlayer(uuid);
                } else {
                  ringer.setRingerPlaying(false);
                }
              }
            }
          }
        }
      }
      stopRingers.forEach(this::stopRinger);
      lastTime = startTime;
      LOGGER.debug("MusicRing.tick end - new lastTime: {}", lastTime);
    }

  public static long clamp(long value, long max, long min) {
    return value < max ? max : Math.min(value, min);
  }

  protected boolean isWaitRinger(UUID ringer) {
    return waitRingers.contains(ringer);
  }

  protected void addRinger(IMusicRinger ringer) {
    if (!ringers.containsKey(ringer.getRingerUUID())) ringers.put(
      ringer.getRingerUUID(),
      ringer
    );
    //System.out.println("add: " + ringer.getRingerUUID() + ", " + ringer);
  }

  public boolean hasRinger(UUID ringer) {
    return ringers.containsKey(ringer);
  }

  public Map<UUID, IMusicRinger> getRingers() {
    return ringers;
  }

  protected void pause() {
    this.pauseTime = System.currentTimeMillis();
  }

  protected void resume() {
    if (this.pauseTime >= 0) this.baseTime +=
      System.currentTimeMillis() - this.pauseTime;
    this.pauseTime = 0;
  }

  private long getTime() {
    return System.currentTimeMillis() - this.baseTime;
  }

  protected void depose() {
    if (
      !playerInfos.isEmpty() &&
      dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled(
        "ringclear"
      )
    ) dev.felnull.imp.client.music.link.LinkDebug.log(
      "ring MusicRing.depose (clears infos, sends no STOP)",
      "infos",
      playerInfos.size()
    );
    ringers.clear();
    playerInfos.clear();
    waitRingers.clear();
    // Last lifecycle point of this ring: drop the fallback counters with the ring.
    loadFailCounts.clear();
    ringSkippedCounts.clear();
  }

  /**
   * [Server-side fallback] The listener whose consecutive "cannot load" count reached {@link #MAX_LOAD_FAIL}, or null if
   * nobody did; server thread only (see {@link #loadFailCounts}).
   */
  private UUID firstFailedPlayer(UUID ringer) {
    var counts = loadFailCounts.get(ringer);
    if (counts == null) return null;
    for (var entry : counts.entrySet()) {
      if (entry.getValue() != null && entry.getValue() >= MAX_LOAD_FAIL) return entry.getKey();
    }
    return null;
  }

  /** Clear only this player's failure streak (the others' streaks are theirs). */
  private void resetLoadFails(UUID ringer, UUID player) {
    var counts = loadFailCounts.get(ringer);
    if (counts != null) counts.remove(player);
  }

  private int getSkippedCount(UUID ringer) {
    return ringSkippedCounts.getOrDefault(ringer, 0);
  }

  private void addSkipped(UUID ringer) {
    ringSkippedCounts.merge(ringer, 1, Integer::sum);
  }

  /**
   * Whether this ringer's mode allows continuing at all (auto play + sequential/shuffle). It only says "a next track is
   * wanted", not that {@code ringerEnd()} picks one, so callers confirm via {@code getRingerMusicSource()}.
   */
  private boolean canSkipTrack(IMusicRinger ringer) {
    if (ringer instanceof IBoomboxRinger boombox) return boombox.shouldAutoContinue();
    return ringer.isRingerLoop();
  }

  protected void restart(UUID uuid) {
    var ringer = ringers.get(uuid);
    if (ringer != null) {
      if (
        dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled(
          "ringrestart:" + uuid
        )
      ) dev.felnull.imp.client.music.link.LinkDebug.log(
        "ring restart",
        "uuid",
        uuid,
        "hadInfos",
        playerInfos.containsKey(uuid)
      );
      stopRinger(uuid);
      addRinger(ringer);
    }
  }

  protected void stopRinger(UUID ringer) {
    //System.out.println("stop: " + ringer + ", " + ringers.get(ringer));
    ringers.remove(ringer);
    waitRingers.remove(ringer);
    if (playerInfos.containsKey(ringer)) {
      playerInfos.get(ringer).depose();
      playerInfos.remove(ringer);
    } else if (
      dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled(
        "ringnoinfos:" + ringer
      )
    ) dev.felnull.imp.client.music.link.LinkDebug.log(
      "ring stopRinger had no infos (no STOP sent)",
      "ringer",
      ringer
    );
  }

  protected void stopRingingPlayer(UUID ringer) {
    waitRingers.remove(ringer);
    if (playerInfos.containsKey(ringer)) {
      playerInfos.get(ringer).depose();
      playerInfos.remove(ringer);
    }
  }

  private MusicTrackerEntry getMusicTracker(IMusicRinger ringer) {
    return ringer.getRingerTracker();
    //   return new MusicTrackerEntry(tr.location(), tr.tracker());
    //        return new MusicPlaybackInfo(tr.getKey(), tr.getValue(), ringer.isRingerMute() ? 0 : ringer.getRingerVolume(), ringer.isRingerMute() ? 0 : ringer.getRingerRange());
  }

  protected void onUpdate(
    ServerPlayer player,
    UUID uuid,
    UUID waitUUID,
    IMPPackets.MusicRingResponseStateType state
  ) {
    var pr = playerInfos.get(uuid);
    if (pr != null && pr.infoUUID.equals(waitUUID)) pr.responseUpdate(
      player,
      state
    );
  }

  protected void addReadyPlayer(
    ServerPlayer player,
    UUID uuid,
    UUID waitUUID,
    boolean result,
    boolean retry,
    long elapsed
  ) {
    var pr = playerInfos.get(uuid);

    if (pr != null && pr.infoUUID.equals(waitUUID)) {
      long st = getTime() - pr.startTime;
      if (st > getMaxWaitTime()) elapsed -= getMaxWaitTime();

      pr.addReadyPlayer(player, result, retry, elapsed);
    }
  }

  private class RingedPlayerInfos {

    private final UUID ringerUUID;
    private final UUID infoUUID = UUID.randomUUID();
    private final List<UUID> firstWaitPlayers = new ArrayList<>();
    private final List<UUID> firstReadyPlayers = new ArrayList<>();
    private final List<UUID> listenPlayers = new ArrayList<>();
    private final List<UUID> middleLoadPlayers = new ArrayList<>();
    private final Map<UUID, Long> failurePlayers = new HashMap<>();
    /**
     * Everyone this ring has handed music to (READY/PLAY sent); the lists above are mutable state a stale client reply
     * can empty (see {@code responseUpdate}), so "who must receive a STOP" is tracked here instead.
     */
    private final Set<UUID> ringedPlayers = new LinkedHashSet<>();
    private final long startTime;
    private boolean notWait;

    public RingedPlayerInfos(UUID ringer, long startTime) {
      this.ringerUUID = ringer;
      this.startTime = startTime;
      firstWaitPlayers.addAll(
        getLevel()
          .players()
          .stream()
          .filter(this::canListen)
          .map(n -> n.getGameProfile().getId())
          .toList()
      );
    }

    private void sendFirstPackets() {
      for (UUID firstWaitPlayer : firstWaitPlayers) {
        ringedPlayers.add(firstWaitPlayer);
        if (
          getLevel().getPlayerByUUID(firstWaitPlayer) instanceof
            ServerPlayer serverPlayer
        ) NetworkManager.sendToPlayer(
          serverPlayer,
          IMPPackets.MUSIC_RING_READY,
          ServerWebDAVProxyManager.createReadyMessage(
            serverPlayer,
            infoUUID,
            ringerUUID,
            getRinger().getRingerMusicSource(),
            getMusicTracker(getRinger()).saveToTag(),
            getRingerPosition()
          ).toRFBB()
        );
      }
    }

    private void sendStopPackets(UUID player) {
      if (
        getLevel().getPlayerByUUID(player) instanceof ServerPlayer serverPlayer
      ) NetworkManager.sendToPlayer(
        serverPlayer,
        IMPPackets.MUSIC_RING_STATE,
        new IMPPackets.MusicRingStateMessage(
          ringerUUID,
          infoUUID,
          IMPPackets.MusicRingStateType.STOP
        ).toRFBB()
      );
    }

    private void sendMiddleStartPacket(UUID player) {
      ringedPlayers.add(player);
      if (
        getLevel().getPlayerByUUID(player) instanceof ServerPlayer serverPlayer
      ) {
        NetworkManager.sendToPlayer(
          serverPlayer,
          IMPPackets.MUSIC_RING_READY,
          ServerWebDAVProxyManager.createReadyMessage(
            serverPlayer,
            infoUUID,
            ringerUUID,
            getRinger().getRingerMusicSource(),
            getMusicTracker(getRinger()).saveToTag(),
            getRingerPosition()
          ).toRFBB()
        );
        advancement(serverPlayer);
      }
    }

    private long getRingerPosition() {
      if (getRinger().isRingerStream()) return 0;
      var sc = getRinger().getRingerMusicSource();
      return clamp(
        getRinger().getRingerPosition(),
        0,
        sc != null ? sc.getDuration() : 0
      );
    }

    private void addReadyPlayer(
      ServerPlayer player,
      boolean result,
      boolean retry,
      long elapsed
    ) {
      // [Server-side fallback] Count this report as "consecutive failures" **of the reporting player only**:
      // reset on that player's success, accumulate on that player's failure, handled in tick(currentTime)
      // (skip next / stop, see MAX_LOAD_FAIL).
      var id = player.getGameProfile().getId();
      if (result) {
        loadFailCounts.computeIfAbsent(ringerUUID, n -> new HashMap<>()).remove(id);
        ringSkippedCounts.remove(ringerUUID);
      } else {
        loadFailCounts.computeIfAbsent(ringerUUID, n -> new HashMap<>())
                .merge(id, 1, Integer::sum);
      }
      if (notWait) {
        if (middleLoadPlayers.contains(id)) {
          if (result) {
            NetworkManager.sendToPlayer(
              player,
              IMPPackets.MUSIC_RING_STATE,
              new IMPPackets.MusicRingStateMessage(
                ringerUUID,
                infoUUID,
                IMPPackets.MusicRingStateType.PLAY,
                getRinger().isRingerStream() ? 0 : elapsed,
                getMusicTracker(getRinger()).saveToTag()
              ).toRFBB()
            );
            listenPlayers.add(id);
            ringedPlayers.add(id);
          } else {
            failurePlayers.put(id, System.currentTimeMillis());
          }
          middleLoadPlayers.remove(id);
        }
      } else {
        if (result) {
          listenPlayers.add(id);
          firstReadyPlayers.add(id);
          ringedPlayers.add(id);
        } else {
          failurePlayers.put(id, System.currentTimeMillis());
        }
        firstWaitPlayers.remove(id);
      }
    }

    private void startReadyWaitPlayers() {
      for (UUID pl : firstReadyPlayers) {
        ringedPlayers.add(pl);
        if (
          getLevel().getPlayerByUUID(pl) instanceof ServerPlayer serverPlayer
        ) {
          NetworkManager.sendToPlayer(
            serverPlayer,
            IMPPackets.MUSIC_RING_STATE,
            new IMPPackets.MusicRingStateMessage(
              ringerUUID,
              infoUUID,
              IMPPackets.MusicRingStateType.PLAY,
              0,
              getMusicTracker(getRinger()).saveToTag()
            ).toRFBB()
          );
          advancement(serverPlayer);
        }
      }
    }

    private void responseUpdate(
      ServerPlayer player,
      IMPPackets.MusicRingResponseStateType state
    ) {
      var id = player.getGameProfile().getId();
      if (listenPlayers.contains(id)) {
        // Only NONE may drop a listener: a LOADING reply can be a stale answer queued on the client thread before it started playing,
        // and dropping on it left the ring with an empty player list, so a later pause sent STOP to nobody while the client kept playing.
        if (state == IMPPackets.MusicRingResponseStateType.NONE) {
          listenPlayers.remove(id);
          if (
            dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled(
              "ringdrop:" + id
            )
          ) dev.felnull.imp.client.music.link.LinkDebug.log(
            "ring drop listener (client reports NONE)",
            "ringer",
            ringerUUID,
            "player",
            id
          );
        } else if (
          state == IMPPackets.MusicRingResponseStateType.LOADING &&
          dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled(
            "ringstale:" + id
          )
        ) dev.felnull.imp.client.music.link.LinkDebug.log(
          "ring keeps listener despite LOADING reply (stale reply guard)",
          "ringer",
          ringerUUID,
          "player",
          id
        );
      }

      if (middleLoadPlayers.contains(id)) {
        if (
          state != IMPPackets.MusicRingResponseStateType.LOADING
        ) middleLoadPlayers.remove(id);
      }
    }

    private void sendUpdate() {
      for (UUID player : listenPlayers) {
        if (
          getLevel().getPlayerByUUID(player) instanceof
            ServerPlayer serverPlayer
        ) NetworkManager.sendToPlayer(
          serverPlayer,
          IMPPackets.MUSIC_RING_STATE,
          new IMPPackets.MusicRingStateMessage(
            ringerUUID,
            infoUUID,
            IMPPackets.MusicRingStateType.UPDATE,
            0,
            getMusicTracker(getRinger()).saveToTag()
          ).toRFBB()
        );
      }
      for (UUID player : middleLoadPlayers) {
        if (
          getLevel().getPlayerByUUID(player) instanceof
            ServerPlayer serverPlayer
        ) NetworkManager.sendToPlayer(
          serverPlayer,
          IMPPackets.MUSIC_RING_STATE,
          new IMPPackets.MusicRingStateMessage(
            ringerUUID,
            infoUUID,
            IMPPackets.MusicRingStateType.UPDATE,
            0,
            getMusicTracker(getRinger()).saveToTag()
          ).toRFBB()
        );
      }
    }

    private boolean tick(long currentTime) {
      // [Server-side fallback] A listener that reported "this track failed to load" MAX_LOAD_FAIL times in a row is assumed unable to play it:
      // if it may continue (auto play on, fewer than MAX_RING_SKIP skips) `ringerEnd()` picks the next track — the skip counts only if the track really changed — otherwise the ring stops.
      // Either way the round ends with `stopRingingPlayer`, so a still-playing ringer is rebuilt with the new source next tick.
      var failedPlayer = firstFailedPlayer(ringerUUID);
      if (failedPlayer != null) {
        resetLoadFails(ringerUUID, failedPlayer);
        var ringer = getRinger();
        if (ringer == null) return true;
        // This is the source the listeners are currently failing on; it is read again after ringerEnd() to tell
        // whether the track really changed.
        var failSrc = ringer.getRingerMusicSource();
        // ⚠️ canSkipTrack() (not isRingerLoop() alone) is only the first gate — "the mode allows continuing".
        // It cannot promise that ringerEnd() has a next track to pick (single-entry playlist, playlist not found,
        // NONE, repeat-one): in those cases it returns silently. So the skip is confirmed by comparing the source.
        boolean canSkip = getSkippedCount(ringerUUID) < MAX_RING_SKIP && canSkipTrack(ringer);
        boolean skipped = false;
        if (canSkip) {
          ringer.ringerEnd();
          // A skip counts **only if the track actually changed**: null or an unchanged source means ringerEnd()
          // did nothing, so it is treated as "stop" instead (and not charged to the skip budget).
          var nextSrc = ringer.getRingerMusicSource();
          skipped = nextSrc != null && !nextSrc.equals(failSrc);
          if (skipped) addSkipped(ringerUUID);
        }
        if (!skipped) ringer.setRingerPlaying(false);
        dev.felnull.imp.client.music.link.LinkDebug.log(
          "ring load-failed fallback",
          "uuid", ringerUUID,
          "src", failSrc == null ? "null" : failSrc.getLoaderType() + ":" + failSrc.getIdentifier(),
          "action", skipped ? "skip-next" : "stop",
          "skipped", getSkippedCount(ringerUUID)
        );
        stopRingingPlayer(ringerUUID);
        return true;
      }

      if (notWait) {
        List<UUID> nl = new ArrayList<>();
        for (ServerPlayer player : getLevel().players()) {
          if (canListen(player)) {
            var id = player.getGameProfile().getId();
            if (!isFailureCoolDown(id)) {
              nl.add(id);
              if (
                !listenPlayers.contains(id) && !middleLoadPlayers.contains(id)
              ) {
                sendMiddleStartPacket(id);
                middleLoadPlayers.add(id);
              }
            }
          }
        }

        for (UUID listenPlayer : listenPlayers) {
          if (!nl.contains(listenPlayer)) sendStopPackets(listenPlayer);
        }

        for (UUID middleLoadPlayer : middleLoadPlayers) {
          if (!nl.contains(middleLoadPlayer)) sendStopPackets(middleLoadPlayer);
        }

        listenPlayers.clear();
        listenPlayers.addAll(
          nl
            .stream()
            .filter(n -> !middleLoadPlayers.contains(n))
            .toList()
        );

        middleLoadPlayers.clear();
        middleLoadPlayers.addAll(
          nl
            .stream()
            .filter(n -> !listenPlayers.contains(n))
            .toList()
        );

        sendUpdate();
        return true;
      }
      if (canPlayPlayersCheck(currentTime)) {
        startReadyWaitPlayers();
        notWait = true;
        return true;
      }
      return false;
    }

    private boolean isFailureCoolDown(UUID player) {
      Long fr = failurePlayers.get(player);
      if (fr != null) return (
        (System.currentTimeMillis() - fr) <= getRetryTime()
      );
      return false;
    }

    private boolean canPlayPlayersCheck(long currentTime) {
      long eq = currentTime - startTime;
      if (eq > getMaxWaitTime()) return true;

      List<UUID> removes = new ArrayList<>();
      for (UUID waiter : firstWaitPlayers) {
        var pl = getLevel().getPlayerByUUID(waiter);
        if (pl == null || !canListen(pl)) removes.add(waiter);
      }
      firstWaitPlayers.removeAll(removes);
      return firstWaitPlayers.isEmpty();
    }

    private void advancement(ServerPlayer player) {
      IMPCriteriaTriggers.LISTEN_TO_MUSIC.trigger(
        player,
        getRinger().isRingerStream(),
        getRinger().isRingerRemote(),
        getEggType()
      );
      ModInvolvementTrigger.trigger(player, IamMusicPlayer.MODID);
    }

    private String getEggType(){
      if(isKamesuta())
        return "kamesuta";
      if(isCat())
        return "cat";
      return "";
    }

    private boolean isCat(){
      var ath = getRinger().getRingerMusicAuthor();
      var source = getRinger().getRingerMusicSource();
      return source != null && Objects.equals(source.getLoaderType(), "netease") &&
                ath != null && ath.contains("AcidGold");
    }

    private boolean isKamesuta() {
      var ath = getRinger().getRingerMusicAuthor();
      return (
        getRinger()
          .getRingerAntenna()
          .getHoverName()
          .getString()
          .equalsIgnoreCase("kamesuta") &&
        ath != null &&
        (ath.equalsIgnoreCase("kamesuta") ||
          ath.equalsIgnoreCase("かめすた") ||
          ath.equalsIgnoreCase("カメスタ"))
      );
    }

    private boolean canListen(Player player) {
      return (
        player.level() == getLevel() && getRinger().canListen((ServerPlayer) player) &&
        Math.sqrt(
          player.distanceToSqr(getRinger().getRingerSpatialPosition())
        ) <=
        (getRinger().isRingerMute() ? 0 : (getRinger().getRingerRange() + 30))
      );
    }

    private IMusicRinger getRinger() {
      return ringers.get(ringerUUID);
    }

    private void depose() {
      // STOP to everyone this ring ever handed music to — the live lists may already have been emptied by a stale
      // client reply, and those players are exactly the ones whose sound would otherwise keep playing.
      var stopTo = new LinkedHashSet<>(ringedPlayers);
      stopTo.addAll(listenPlayers);
      stopTo.addAll(middleLoadPlayers);
      stopTo.addAll(firstReadyPlayers);
      stopTo.addAll(firstWaitPlayers);
      dev.felnull.imp.client.music.link.LinkDebug.log(
        "ring depose (sends STOP)",
        "ringer",
        ringerUUID,
        "stopTo",
        stopTo.size(),
        "ringed",
        ringedPlayers.size(),
        "listen",
        listenPlayers.size()
      );
      for (UUID stopPlayer : stopTo) {
        sendStopPackets(stopPlayer);
      }
      ringedPlayers.clear();
    }
  }

  public static long getMaxWaitTime() {
    return IamMusicPlayer.getConfig().maxWaitTime;
  }

  public static long getRetryTime() {
    return IamMusicPlayer.getConfig().retryTime;
  }
}
