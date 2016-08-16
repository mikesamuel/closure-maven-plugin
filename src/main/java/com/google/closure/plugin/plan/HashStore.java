package com.google.closure.plugin.plan;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.maven.plugin.logging.Log;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/**
 * A persisted store of hashes that can be used to compare previous builds of
 * {@link Step}s to decide whether to rebuild.
 */
public final class HashStore {
  private final ConcurrentMap<String, Hash> hashes = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Hash> stored = new ConcurrentHashMap<>();

  /** The previous hash of a step's inputs if available. */
  Optional<Hash> getHash(PlanKey stepKey) {
    return Optional.fromNullable(hashes.get(stepKey.text));
  }

  /** Store the hash of a step's inputs. */
  void setHash(PlanKey stepKey, Hash hash) {
    hashes.put(stepKey.text, Preconditions.checkNotNull(hash));
    stored.put(stepKey.text, Preconditions.checkNotNull(hash));
  }

  /**
   * Reads a hashStore from a reader which supplies JSON mapping step keys
   * to hashes, both represented as strings.  The hashes are hex strings.
   */
  public static HashStore read(Reader in, Log log) throws IOException {
    HashStore hs = new HashStore();

    JSONParser p = new JSONParser();
    Object result;
    try {
      result = p.parse(in);
    } catch (ParseException ex) {
      throw new IOException("Malformed hash store", ex);
    }
    boolean wellFormed = true;
    if (result instanceof Map<?, ?>) {
      for (Map.Entry<?, ?> e : ((Map<?, ?>) result).entrySet()) {
        Object k = e.getKey();
        Object v = e.getValue();
        if (!(k instanceof String)) {
          log.error(
              "Bad hash store key " + k + " : "
              + (k != null ? k.getClass() : null));
          wellFormed = false;
          continue;
        }
        if (!(v instanceof String)) {
          log.error("Bad hash store value " + v + " : "
              + (v != null ? v.getClass() : null));
          wellFormed = false;
          continue;
        }
        Hash h = new Hash((String) v);
        Hash old = hs.hashes.put((String) k, h);
        if (old != null) {
          log.error("Multiple hashes for key " + k);
          wellFormed = false;
        }
      }
    } else {
      log.error("Hash store was not a JSON map");
      wellFormed = false;
    }
    if (!wellFormed) {
      throw new IOException("Malformed hash store");
    }
    return hs;
  }

  /**
   * Writes a form that can be read by {@link #read}.
   * This only writes the keys stored this session because those are the only
   * ones known to be related to steps that were run or explicitly skipped
   * instead of steps whose key depended on stale configuration or files
   * that no longer exist and so which no longer relate to the output in a
   * meaningful way.
   * <p>
   * If a later compile were to re-encounter a step with such a defunct key,
   * for example because a configuration file was reverted, then we would
   * not want to assume that the step does not need to be rerun.
   */
  public void write(Writer out) throws IOException {
    ImmutableMap.Builder<String, String> strStrMap = ImmutableMap.builder();
    for (Map.Entry<String, Hash> e : this.stored.entrySet()) {
      strStrMap.put(e.getKey(), e.getValue().toString());
    }
    JSONObject.writeJSONString(strStrMap.build(), out);
  }
}
