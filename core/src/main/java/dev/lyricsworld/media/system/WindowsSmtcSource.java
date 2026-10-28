package dev.lyricsworld.media.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lyricsworld.media.PlaybackState;
import dev.lyricsworld.util.Json;
import dev.lyricsworld.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Windows: System Media Transport Controls (the same data the volume flyout shows), read through a
 * long-lived hidden Windows PowerShell 5.1 process that prints one JSON line per second.
 * Works for Spotify, Apple Music for Windows, browsers, etc.
 */
final class WindowsSmtcSource extends SystemMediaSource {
    private static final String SCRIPT = String.join("\n",
        "$ErrorActionPreference='Stop'",
        "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8",
        "Add-Type -AssemblyName System.Runtime.WindowsRuntime",
        "$asTask=([System.WindowsRuntimeSystemExtensions].GetMethods()|Where-Object{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'})[0]",
        "function Await($op,$type){$t=$asTask.MakeGenericMethod($type).Invoke($null,@($op));$t.Wait(-1)|Out-Null;$t.Result}",
        "[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]",
        "[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]",
        "$mgr=Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
        "while($true){",
        "  $out=@()",
        "  try{",
        "    foreach($s in $mgr.GetSessions()){",
        "      $p=Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
        "      $tl=$s.GetTimelineProperties()",
        "      $pi=$s.GetPlaybackInfo()",
        "      $playing=($pi.PlaybackStatus -eq 4)",
        "      $pos=$tl.Position.TotalMilliseconds",
        "      if($playing){$pos+=([DateTimeOffset]::UtcNow-$tl.LastUpdatedTime).TotalMilliseconds}",
        "      $out+=[pscustomobject]@{app=$s.SourceAppUserModelId;title=$p.Title;artist=$p.Artist;album=$p.AlbumTitle;pos=[long]$pos;dur=[long]($tl.EndTime-$tl.StartTime).TotalMilliseconds;playing=$playing}",
        "    }",
        "  }catch{}",
        "  Write-Output (ConvertTo-Json -InputObject @($out) -Compress -Depth 3)",
        "  [Console]::Out.Flush()",
        "  Start-Sleep -Milliseconds 700",
        "}");

    private Process proc;
    private Thread reader;
    private volatile List<Candidate> latest = Collections.emptyList();
    private volatile boolean started;

    WindowsSmtcSource(String label, String filter) { super(label, filter); }

    @Override public boolean isAvailable() { return true; }

    @Override
    public synchronized PlaybackState poll() {
        if (!started) start();
        return Candidate.pick(latest, filter, "Windows");
    }

    private void start() {
        started = true;
        try {
            byte[] utf16 = SCRIPT.getBytes(StandardCharsets.UTF_16LE);
            String enc = java.util.Base64.getEncoder().encodeToString(utf16);
            proc = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc)
                    .redirectErrorStream(true).start();
            final BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
            reader = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        String line;
                        while ((line = br.readLine()) != null) handleLine(line.trim());
                    } catch (IOException ignored) {
                        // process ended
                    }
                    error = "Windows media session helper stopped";
                    started = false;
                }
            }, "LyricsWorld-smtc");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            error = "Cannot start PowerShell: " + e.getMessage();
            Log.warn("SMTC helper failed to start", e);
            started = false;
        }
    }

    private void handleLine(String line) {
        if (line.isEmpty() || (line.charAt(0) != '[' && line.charAt(0) != '{')) {
            if (!line.isEmpty()) error = "SMTC: " + line;
            return;
        }
        try {
            error = null;
            latest = parse(line, System.nanoTime());
        } catch (RuntimeException e) {
            error = "Unreadable SMTC data";
        }
    }

    static List<Candidate> parse(String json, long nanos) {
        JsonElement root = Json.parse(json);
        List<Candidate> out = new ArrayList<Candidate>();
        JsonArray arr = new JsonArray();
        if (root.isJsonArray()) arr = root.getAsJsonArray();
        else if (root.isJsonObject()) arr.add(root);
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            out.add(new Candidate(Json.str(o, "app", ""), Json.str(o, "title", ""), Json.str(o, "artist", ""),
                    Json.str(o, "album", ""), Json.num(o, "dur", 0), Json.num(o, "pos", 0),
                    Json.bool(o, "playing", false), nanos));
        }
        return out;
    }

    @Override
    public synchronized void close() {
        if (proc != null) proc.destroyForcibly();
        started = false;
    }
}
