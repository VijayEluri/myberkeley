package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@Service(value = Servlet.class)
@SlingServlet(paths = {"/system/myberkeley/caldav"}, methods = {"GET"}, generateComponent = true, generateService = true)

public class CalDavGetServlet extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavGetServlet.class);

    public enum REQUEST_PARAMS {
        type,
        mode,
        start_date,
        end_date
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        // Keep out anon users.
        if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Anonymous users can't use the CalDAV Proxy Service.");
            return;
        }

        // TODO set the correct username instead of hardcoding vbede
        CalDavConnector connector = new CalDavConnector("vbede", "bedework",
                new URI("http://test.media.berkeley.edu:8080", false),
                new URI("http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/", false));

        CalendarSearchCriteria criteria = getCalendarSearchCriteria(request);

        try {
            handleGet(response, connector, criteria);
        } finally {
            response.getWriter().close();
        }
    }

    protected CalendarSearchCriteria getCalendarSearchCriteria(SlingHttpServletRequest request) throws ServletException {
        Date defaultStart = new DateTime();
        Date defaultEnd = new DateTime();
        CalendarSearchCriteria criteria = new CalendarSearchCriteria(CalendarSearchCriteria.TYPE.VEVENT,
                defaultStart, defaultEnd, CalendarSearchCriteria.MODE.ALL_UNARCHIVED);

        // apply non-default values from request if they're available

        RequestParameter type = request.getRequestParameter(REQUEST_PARAMS.type.toString());
        if (type != null) {
            criteria.setType(CalendarSearchCriteria.TYPE.valueOf(type.getString()));
        }
        RequestParameter mode = request.getRequestParameter(REQUEST_PARAMS.mode.toString());
        if (mode != null) {
            criteria.setMode(CalendarSearchCriteria.MODE.valueOf(mode.getString()));
        }
        RequestParameter startDate = request.getRequestParameter(REQUEST_PARAMS.start_date.toString());
        if (startDate != null) {
            try {
                criteria.setStart(new DateTime(startDate.getString()));
            } catch (ParseException pe) {
                throw new ServletException("Invalid start date passed: " + startDate.getString(), pe);
            }
        }
        RequestParameter endDate = request.getRequestParameter(REQUEST_PARAMS.end_date.toString());
        if (endDate != null) {
            try {
                criteria.setEnd(new DateTime(endDate.getString()));
            } catch (ParseException pe) {
                throw new ServletException("Invalid end date passed: " + endDate.getString(), pe);
            }
        }

        return criteria;
    }

    protected void handleGet(SlingHttpServletResponse response, CalDavConnector connector,
                             CalendarSearchCriteria criteria) throws IOException {
        List<CalendarWrapper> calendars;
        boolean hasOverdue;

        try {
            calendars = connector.searchByDate(criteria);
            hasOverdue = connector.hasOverdueTasks();
        } catch (Exception e) {
            LOGGER.error("Exception fetching calendars", e);
            response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            JSONObject json = new JSONObject();
            JSONArray results = new JSONArray();
            for (CalendarWrapper wrapper : calendars) {
                results.put(wrapper.toJSON());
            }
            json.put("results", results);
            json.put("hasOverdueTasks", hasOverdue);

            LOGGER.info("CalDavGetServlet's JSON response: " + json.toString(2));
            response.getWriter().write(json.toString(2));
        } catch (JSONException je) {
            LOGGER.error("Failed to convert calendar to JSON", je);
        }

    }

}