package nl.opengeogroep.safetymaps.server.security;

import nl.opengeogroep.safetymaps.server.db.Cfg;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.naming.NamingException;
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
import java.io.IOException;
import java.security.Principal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

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

    private static RoleProvider roleProvider;

    private static final ConcurrentMap<String,HttpSession> CONTAINER_SESSIONS = new ConcurrentHashMap();

    private static final String SESSION_UPDATABLE_PRINCIPAL = UpdatableLoginSessionFilter.class.getName() + ".PRINCIPAL";

    private static final Collection<SessionInvalidateMonitor> sessionInvalidateMonitors = new ArrayList();

    private static long lastInactiveSessionsPruned;

    private static final long INACTIVE_SESSIONS_PRUNE_INTERVAL = 10 * 60 * 1000;

    public static class UpdatablePrincipal implements Principal {

        private String name;
        private Collection<String> roles;

        public UpdatablePrincipal(String name, Collection<String> roles) {
            this.name = name;
            this.roles = roles;
        }

        public Collection<String> getRoles() {
            return roles;
        }

        // For JSTL property in authinfo.jsp
        public void setRoles(Collection<String> roles) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return String.format("UpdatablePrincipal [name=%s, roles=%s]", getName(), roles);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
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

        UpdatablePrincipal principal = (UpdatablePrincipal)session.getAttribute(SESSION_UPDATABLE_PRINCIPAL);

        if(principal != null) {
            chainWithPrincipal(request, response, chain, principal);
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
            Collection<String> roles = new HashSet(roleProvider.getRoles(request.getRemoteUser()));

            for(String role: getExternalRolesAsUsersForGroupMembership()) {
                if(request.isUserInRole(role)) {
                    roles.addAll(roleProvider.getRoles(role));
                    // Make sure when updating role membership, we can check whether
                    // the user was in this role again
                    roles.add(role);
                }
            }

            principal = new UpdatablePrincipal(request.getRemoteUser(), roles);
            session.setAttribute(SESSION_UPDATABLE_PRINCIPAL, principal);

            chainWithPrincipal(request, response, chain, principal);
        } catch(Exception e) {
            throw new ServletException(e);
        }
    }

    private static String[] getExternalRolesAsUsersForGroupMembership() throws SQLException, NamingException {
        String[] externalRolesAsUsersForGroupMembership = Cfg.getSetting("external_roles_as_users_for_group_membership", "").split(",");
        for(int i = 0; i < externalRolesAsUsersForGroupMembership.length; i++) {
            externalRolesAsUsersForGroupMembership[i] = externalRolesAsUsersForGroupMembership[i].trim();
        }
        return externalRolesAsUsersForGroupMembership;
    }

    private static void addExternalRolesAsUsersForGroupMembershipToNewRoles(Collection<String> previousRoles, Collection<String> newRoles) throws Exception {
        for(String role: getExternalRolesAsUsersForGroupMembership()) {
            if(previousRoles.contains(role)) {
                newRoles.addAll(roleProvider.getRoles(role));
                newRoles.add(role);
            }
        }
    }

    private static void chainWithPrincipal(final HttpServletRequest request, HttpServletResponse response, FilterChain chain, final UpdatablePrincipal principal) throws IOException, ServletException {
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return principal.getRoles().contains(role);
            }

            @Override
            public String getRemoteUser() {
                return principal.getName();
            }
        }, response);
    }

    public static void invalidateUserSessions(String name) throws IOException {
        List<String> invalidSessionIds = new ArrayList<>();
        for(HttpSession session: CONTAINER_SESSIONS.values()) {
            try {
                UpdatablePrincipal principal = (UpdatablePrincipal)session.getAttribute(SESSION_UPDATABLE_PRINCIPAL);
                if (principal != null) {
                    log.debug("Checking container session " + session.getId() + " to delete, session principal = " + principal);
                    if (name.equals(principal.getName())) {
                        log.info("Invalidating container session for user " + name + ": " + session.getId());
                        session.invalidate();
                        invalidSessionIds.add(session.getId());
                    }
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

    private static void updatePrincipalRoles(UpdatablePrincipal principalToUpdate, HttpSession session, Function<String,Collection<String>> roleProvider) throws Exception {
        Collection<String> previousRoles = principalToUpdate.getRoles();
        Collection<String> newRoles = roleProvider.apply(principalToUpdate.getName());
        if (newRoles == null) {
            newRoles = new HashSet<>();
        }
        addExternalRolesAsUsersForGroupMembershipToNewRoles(previousRoles, newRoles);
        // Create a new instance, do not update the roles of the previous instance so active requests
        // do not get updated roles for consistency...
        session.setAttribute(SESSION_UPDATABLE_PRINCIPAL, new UpdatablePrincipal(principalToUpdate.getName(), newRoles));
    }

    public static void updateUserSessionRoles(String username) throws Exception {
        if(roleProvider == null) {
            return;
        }

        synchronized(CONTAINER_SESSIONS) {

            for(HttpSession session: CONTAINER_SESSIONS.values()) {
                try {
                    UpdatablePrincipal principal = (UpdatablePrincipal)session.getAttribute(SESSION_UPDATABLE_PRINCIPAL);

                    if(principal != null) {
                        boolean needsUpdate = principal.getName().equals(username);

                        Collection<String> previousRoles = principal.getRoles();

                        // Also update session when updating roles of a user with a name that is an external role
                        // which is used for role membership

                        String[] externalRoleNamesForGroupMembership = getExternalRolesAsUsersForGroupMembership();
                        for(String role: externalRoleNamesForGroupMembership) {
                            if(role.equals(username) && previousRoles.contains(role)) {
                                needsUpdate = true;
                            }
                        }

                        if(needsUpdate) {
                            updatePrincipalRoles(principal, session, roleProvider::getRoles);
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
                    UpdatablePrincipal principal = (UpdatablePrincipal)session.getAttribute(SESSION_UPDATABLE_PRINCIPAL);

                    if(principal != null) {
                        updatePrincipalRoles(principal, session, rolesByUsername::get);
                        Collection<String> previousRoles = principal.getRoles();
                        Collection<String> newRoles = new HashSet();
                        Collection<String> myRoles = rolesByUsername.get(principal.getName());

                        if (myRoles != null) {
                            newRoles.addAll(rolesByUsername.get(principal.getName()));
                        }
                        addExternalRolesAsUsersForGroupMembershipToNewRoles(previousRoles, newRoles);
                        principal = new UpdatablePrincipal(principal.getName(), newRoles);
                        session.setAttribute(SESSION_UPDATABLE_PRINCIPAL, principal);
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
