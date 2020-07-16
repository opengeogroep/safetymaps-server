package nl.opengeogroep.safetymaps.server.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Filter that keeps all container sessions and can invalidate them when an account
 * is deleted, or update roles when role membership is updated.
 *
 * Supports getting role membership based on a fixed role set by external logins
 * using that role as the username. For instance a LDAPUser role will cause roles
 * that the user with name LDAPUser is a member of to be added to the login
 * session.
 *
 * Role membership data by username is provided by an external class configured
 * by class name.
 *
 * Note that only those roles provided by the role provider and original roles for
 * which group membership as a username was checked will return true for chained
 * request.isUserInRole() calls, other roles the original request.isUserInRole()
 * would return true for are dropped. These original roles would not be re-checked
 * for the duration of the session, and that would be inconsistent with the purpose
 * of this filter.
 *
 * @author matthijsln
 */
public class UpdatableLoginSessionFilter implements Filter {

    private static final Log log = LogFactory.getLog(UpdatableLoginSessionFilter.class);

    private static String[] externalRoleNamesForGroupMembership = new String[] {};

    private static RoleProvider roleProvider;

    private static final ConcurrentMap<String,HttpSession> CONTAINER_SESSIONS = new ConcurrentHashMap();

    private static final String SESSION_ATTR_ROLES = UpdatableLoginSessionFilter.class.getName() + ".ROLES";
    private static final String SESSION_ATTR_USERNAME = UpdatableLoginSessionFilter.class.getName() + ".USERNAME";

    private static final Collection<SessionInvalidateMonitor> sessionInvalidateMonitors = new ArrayList();

    private static long lastInactiveSessionsPruned;

    private static final long INACTIVE_SESSIONS_PRUNE_INTERVAL = 10 * 60 * 1000;

    @Override
    public void init(FilterConfig filterConfig) {

        String s = filterConfig.getInitParameter("externalRolesForGroupMembership");

        if(s != null) {
            externalRoleNamesForGroupMembership = s.split(",");
        }

        String providerClass = filterConfig.getInitParameter("roleProviderClass");
        if(providerClass != null) {
            try {
                roleProvider = (RoleProvider)Class.forName(providerClass).newInstance();
            } catch(Exception e) {
                log.error("Cannot instantiate role provider class " + providerClass, e);
            }
        }

        lastInactiveSessionsPruned = System.currentTimeMillis();
    }

    private void pruneInactiveSessions() {
        long current = System.currentTimeMillis();

        if(current - lastInactiveSessionsPruned > INACTIVE_SESSIONS_PRUNE_INTERVAL) {
            lastInactiveSessionsPruned = current;

            synchronized(CONTAINER_SESSIONS) {
                List<String> sessionIdsToRemove = new ArrayList();
                for(Map.Entry<String,HttpSession> entry: CONTAINER_SESSIONS.entrySet()) {
                    HttpSession session = entry.getValue();
                    try {
                        if(current - session.getLastAccessedTime() > session.getMaxInactiveInterval() * 1000) {
                            sessionIdsToRemove.add(entry.getKey());
                        }
                    } catch(IllegalStateException e) {
                        // Session already invalidated
                        sessionIdsToRemove.add(entry.getKey());
                    }
                }
                if(!sessionIdsToRemove.isEmpty()) {
                    for(String id: sessionIdsToRemove) {
                        CONTAINER_SESSIONS.remove(id);
                    }
                    log.debug("Pruned " + sessionIdsToRemove + " inactive sessions");
                }
            }

        }

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        HttpSession session = request.getSession();

        pruneInactiveSessions();

        // Save the session, so it can be invalidated when the user account is
        // deleted

        CONTAINER_SESSIONS.putIfAbsent(session.getId(), session);

        if(roleProvider == null) {
            chain.doFilter(request, response);
            return;
        }

        // Check if we already processed the session the first time it was authenticated

        Collection<String> roles = (Collection<String>)session.getAttribute(SESSION_ATTR_ROLES);

        if(roles != null) {
            chainWithRoles(request, response, chain, roles);
            return;
        }

        // If not authenticated, pass through

        if(request.getUserPrincipal() == null) {
            chain.doFilter(request, response);
            return;
        }

        // User is authenticated, get roles from provider and save them in the
        // session, so they can be updated when membership changes

        try {
            roles = new HashSet(roleProvider.getRoles(request.getRemoteUser()));

            for(String role: externalRoleNamesForGroupMembership) {
                if(request.isUserInRole(role)) {
                    roles.addAll(roleProvider.getRoles(role));
                    // Make sure when updating role membership, we can check wether
                    // the user was in this role again
                    roles.add(role);
                }
            }

            session.setAttribute(SESSION_ATTR_USERNAME, request.getRemoteUser());
            session.setAttribute(SESSION_ATTR_ROLES, roles);

            chainWithRoles(request, response, chain, roles);
        } catch(Exception e) {
            throw new ServletException(e);
        }
    }

    private static void chainWithRoles(final HttpServletRequest request, HttpServletResponse response, FilterChain chain, final Collection<String> roles) throws IOException, ServletException {
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public boolean isUserInRole(String role) {
                return roles.contains(role);
            }
        }, response);
    }

    public static void invalidateUserSessions(String name) throws IOException {
        List<String> invalidSessionIds = new ArrayList<>();
        for(HttpSession session: CONTAINER_SESSIONS.values()) {
            try {
                String sessionUser = (String)session.getAttribute(SESSION_ATTR_USERNAME);
                log.debug("Checking container session " + session.getId() + " to delete, session user = " + sessionUser);
                if(name.equals(sessionUser)) {
                    log.info("Invalidating container session for user " + name + ": " + session.getId());
                    session.invalidate();
                    invalidSessionIds.add(session.getId());
                }
            } catch(IllegalStateException e) {
                log.info("Session already invalid: " + session.getId());
                invalidSessionIds.add(session.getId());
            }
        }
        for(String id: invalidSessionIds) {
            CONTAINER_SESSIONS.remove(id);
        }

        for(SessionInvalidateMonitor monitor: sessionInvalidateMonitors) {
            monitor.invalidateUserSessions(name);
        }
    }

    public static void monitorSessionInvalidation(SessionInvalidateMonitor monitor) {
        sessionInvalidateMonitors.add(monitor);
    }

    public static void updateUserSessionRoles(String username) throws Exception {
        if(roleProvider == null) {
            return;
        }

        synchronized(CONTAINER_SESSIONS) {

            for(HttpSession session: CONTAINER_SESSIONS.values()) {
                try {
                    String sessionUsername = (String)session.getAttribute(SESSION_ATTR_USERNAME);

                    if(sessionUsername != null) {
                        boolean needsUpdate = sessionUsername.equals(username);

                        Collection<String> previousRoles = (Collection<String>)session.getAttribute(SESSION_ATTR_ROLES);

                        // Also update session when updating roles of user that is used
                        // for role membership by setting a role in the original request

                        for(String role: externalRoleNamesForGroupMembership) {
                            if(role.equals(username) && previousRoles.contains(role)) {
                                needsUpdate = true;
                            }
                        }

                        if(needsUpdate) {
                            Collection<String> newRoles = new HashSet();

                            newRoles.addAll(roleProvider.getRoles(username));

                            for(String role: externalRoleNamesForGroupMembership) {
                                if(previousRoles.contains(role)) {
                                    newRoles.addAll(roleProvider.getRoles(role));
                                    newRoles.add(role);
                                }
                            }
                            session.setAttribute(SESSION_ATTR_ROLES, newRoles);
                        }
                    }
                } catch(IllegalStateException e) {
                    // Session was invalid
                }
            }
        }
    }

    public static void updateAllSessionRoles() throws Exception {
        if(roleProvider == null) {
            return;
        }

        Map<String, Collection<String>> rolesByUsername = roleProvider.getAllRolesByUsername();

        synchronized(CONTAINER_SESSIONS) {
            for(HttpSession session: CONTAINER_SESSIONS.values()) {
                try {
                    String username = (String)session.getAttribute(SESSION_ATTR_USERNAME);

                    if(username != null) {
                        Collection<String> previousRoles = (Collection<String>)session.getAttribute(SESSION_ATTR_ROLES);
                        Collection<String> newRoles = new HashSet();
                        Collection<String> myRoles = rolesByUsername.get(username);

                        if (myRoles != null) {
                            newRoles.addAll(rolesByUsername.get(username));
                        }

                        for(String role: externalRoleNamesForGroupMembership) {
                            if(previousRoles.contains(role)) {
                                newRoles.addAll(rolesByUsername.get(role));
                                newRoles.add(role);
                            }
                        }
                        session.setAttribute(SESSION_ATTR_ROLES, newRoles);
                    }
                } catch(IllegalStateException e) {
                    // Session was invalid
                }
            }
        }
    }

    @Override
    public void destroy() {
    }
}
