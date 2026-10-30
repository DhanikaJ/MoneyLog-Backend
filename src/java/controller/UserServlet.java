package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hibernate.Session;
import org.hibernate.Transaction;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hibernate.Users;
import hibernate.HibernateUtil;
import java.util.Date;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

@WebServlet(name = "UserServlet", urlPatterns = {"/UserServlet"})

public class UserServlet extends HttpServlet {

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
        Transaction transaction = null;

        try {
            // Read JSON from request body
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }

            String jsonString = jsonBuffer.toString();
            System.out.println("Received signup data: " + jsonString);

            if (jsonString.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Request body is empty");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Parse JSON
            JsonObject signupData = JsonParser.parseString(jsonString).getAsJsonObject();

            // Extract user data
            String username = signupData.has("username") ? signupData.get("username").getAsString().trim() : "";
            String email = signupData.has("email") ? signupData.get("email").getAsString().trim() : "";
            String password = signupData.has("password") ? signupData.get("password").getAsString() : "";

            // Validate input
            if (username.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Username is required");
                out.print(gson.toJson(errorResponse));
                return;
            }

            if (username.length() < 3) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Username must be at least 3 characters long");
                out.print(gson.toJson(errorResponse));
                return;
            }

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

            if (password.length() < 6) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Password must be at least 6 characters long");
                out.print(gson.toJson(errorResponse));
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            // Check if email already exists
            Criteria emailCriteria = session.createCriteria(Users.class);
            emailCriteria.add(Restrictions.eq("email", email.toLowerCase()));
            Users existingUserByEmail = (Users) emailCriteria.uniqueResult();

            if (existingUserByEmail != null) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Email already exists");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Check if username already exists
            Criteria usernameCriteria = session.createCriteria(Users.class);
            usernameCriteria.add(Restrictions.eq("username", username));
            Users existingUserByUsername = (Users) usernameCriteria.uniqueResult();

            if (existingUserByUsername != null) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Username already exists");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Hash the password
            String hashedPassword = hashPassword(password);

            // Create new user
            Users newUser = new Users();
            newUser.setUsername(username);
            newUser.setEmail(email.toLowerCase());
            newUser.setPassword_hash(hashedPassword);
            newUser.setCreated_at(new Date());
            newUser.setUpdated_at(new Date());

            // Save user to database
            session.save(newUser);
            transaction.commit();

            // Return success response
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "User created successfully");
            successResponse.addProperty("id", newUser.getId());
            successResponse.addProperty("username", newUser.getUsername());
            successResponse.addProperty("email", newUser.getEmail());

            response.setStatus(HttpServletResponse.SC_CREATED);
            out.print(gson.toJson(successResponse));

        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }

            System.err.println("Error in UserServlet: " + e.getMessage());
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
