package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hibernate.HibernateUtil;
import hibernate.Users;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;

@WebServlet(name = "LoginServlet", urlPatterns = {"/LoginServlet"})
public class LoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        PrintWriter out = response.getWriter();
        Session session = null;

        try {
            // Read JSON from request body
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }

            String jsonString = jsonBuffer.toString();
            System.out.println("Received login data: " + jsonString);

            if (jsonString.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Request body is empty");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Parse JSON
            JsonObject loginData = JsonParser.parseString(jsonString).getAsJsonObject();

            // Extract login credentials
            String email = loginData.has("email") ? loginData.get("email").getAsString().trim() : "";
            String password = loginData.has("password") ? loginData.get("password").getAsString() : "";

            // Validate input
            if (email.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Email is required");
                out.print(gson.toJson(errorResponse));
                return;
            }

            if (password.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Password is required");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Hash the password for comparison
            String hashedPassword = hashPassword(password);

            session = HibernateUtil.getSessionFactory().openSession();

            // Find user by email
            Criteria criteria = session.createCriteria(Users.class);
            criteria.add(Restrictions.eq("email", email.toLowerCase()));
            Users user = (Users) criteria.uniqueResult();

            if (user == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Invalid email or password");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Verify password
            if (!user.getPassword_hash().equals(hashedPassword)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Invalid email or password");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Successful login - return user data (without password)
            JsonObject userResponse = new JsonObject();
            userResponse.addProperty("success", true);
            userResponse.addProperty("id", user.getId());
            userResponse.addProperty("username", user.getUsername());
            userResponse.addProperty("email", user.getEmail());
            userResponse.addProperty("message", "Login successful");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(userResponse));

        } catch (Exception e) {
            System.err.println("Error in LoginServlet: " + e.getMessage());
            e.printStackTrace();

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("message", "Internal server error: " + e.getMessage());
            out.print(gson.toJson(errorResponse));
        } finally {
            if (session != null) {
                session.close();
            }
            out.flush();
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    // Hash password using SHA-256
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());

            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
}
