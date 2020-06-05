package nl.opengeogroep.safetymaps.server.security;

/**
 *
 * @author matthijsln
 */
public interface SessionInvalidateMonitor {
    public void invalidateUserSessions(String username);
}
