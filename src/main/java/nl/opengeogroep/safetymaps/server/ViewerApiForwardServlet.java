package nl.opengeogroep.safetymaps.server;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 *
 * @author matthijsln
 */
public class ViewerApiForwardServlet extends HttpServlet {

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    public final void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        RequestDispatcher dispatcher = request.getRequestDispatcher("/api" + request.getPathInfo());
        dispatcher.forward(request, response);
    }
}
