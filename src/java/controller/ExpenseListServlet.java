package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import hibernate.Expense;
import hibernate.HibernateUtil;
import hibernate.Users;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

@WebServlet(name = "ExpenseListServlet", urlPatterns = {"/ExpenseListServlet"})
public class ExpenseListServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        PrintWriter out = response.getWriter();
        Session session = null;

        try {
            System.out.println("ExpenseListServlet doGet called");
            // Get user ID from request parameter
            String userIdParam = request.getParameter("userId");
            System.out.println("Received userId parameter: " + userIdParam);
            if (userIdParam == null || userIdParam.trim().isEmpty()) {
                System.out.println("User ID is null or empty");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "User ID is required");
                out.print(gson.toJson(errorResponse));
                return;
            }

            int userId;
            try {
                userId = Integer.parseInt(userIdParam);
                System.out.println("Parsed userId: " + userId);
            } catch (NumberFormatException e) {
                System.out.println("Invalid userId format: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Invalid user ID format");
                out.print(gson.toJson(errorResponse));
                return;
            }

            System.out.println("Opening Hibernate session...");
            session = HibernateUtil.getSessionFactory().openSession();
            
            // IMPORTANT: Clear session cache to ensure fresh data
            session.clear();
            
            System.out.println("Hibernate session opened successfully");

            // Verify user exists
            System.out.println("Checking if user exists...");
            Criteria userCriteria = session.createCriteria(Users.class);
            userCriteria.add(Restrictions.eq("id", userId));
            userCriteria.setCacheable(false); // Force fresh query
            Users user = (Users) userCriteria.uniqueResult();

            if (user == null) {
                System.out.println("User not found with ID: " + userId);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "User not found");
                out.print(gson.toJson(errorResponse));
                return;
            }

            System.out.println("User found: " + user.getUsername());

            // Get all expenses for the user
            System.out.println("Fetching expenses for user...");
            Criteria expenseCriteria = session.createCriteria(Expense.class);
            expenseCriteria.createAlias("category", "c");
            expenseCriteria.add(Restrictions.eq("users.id", userId));
            expenseCriteria.addOrder(Order.desc("created_at")); // Latest first by default
            expenseCriteria.setCacheable(false); // Force fresh query

            List<Expense> expenses = expenseCriteria.list();

            // Convert expenses to JSON-friendly format
            List<Map<String, Object>> expensesList = new ArrayList<>();
            System.out.println("Found " + expenses.size() + " expenses");
            for (Expense expense : expenses) {
                System.out.println("Processing expense ID: " + expense.getId());
                Map<String, Object> expenseMap = new HashMap<>();
                expenseMap.put("id", expense.getId());
                expenseMap.put("amount", expense.getAmount());
                expenseMap.put("description", expense.getDescription() != null ? expense.getDescription() : "");
                expenseMap.put("dates", expense.getDates().toString());
                expenseMap.put("createdAt", expense.getCreated_at().toString());
                expenseMap.put("updatedAt", expense.getUpdated_at().toString());

                if (expense.getCategory() != null) {
                    Map<String, Object> categoryMap = new HashMap<>();
                    categoryMap.put("id", expense.getCategory().getId());
                    categoryMap.put("name", expense.getCategory().getName());
                    expenseMap.put("category", categoryMap);
                } else {
                    System.out.println("Warning: Expense " + expense.getId() + " has no category");
                    Map<String, Object> categoryMap = new HashMap<>();
                    categoryMap.put("id", 0);
                    categoryMap.put("name", "Unknown");
                    expenseMap.put("category", categoryMap);
                }

                expensesList.add(expenseMap);
            }

            // Create response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Expenses fetched successfully");
            responseData.put("expenses", expensesList);
            responseData.put("totalCount", expenses.size());

            System.out.println("Sending response with " + expensesList.size() + " expenses");
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(responseData));

        } catch (Exception e) {
            System.err.println("Error in ExpenseListServlet: " + e.getMessage());
            e.printStackTrace();

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("message", "Internal server error: " + e.getMessage());
            out.print(gson.toJson(errorResponse));
        } finally {
            if (session != null) {
                session.close();
                System.out.println("Hibernate session closed");
            }
            out.flush();
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        PrintWriter out = response.getWriter();
        Session session = null;
        Transaction transaction = null;

        try {
            System.out.println("ExpenseListServlet doDelete called");
            
            // Get parameters
            String expenseIdParam = request.getParameter("expenseId");
            String userIdParam = request.getParameter("userId");

            System.out.println("Delete request - ExpenseId: " + expenseIdParam + ", UserId: " + userIdParam);

            if (expenseIdParam == null || userIdParam == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Expense ID and User ID are required");
                out.print(gson.toJson(errorResponse));
                return;
            }

            int expenseId = Integer.parseInt(expenseIdParam);
            int userId = Integer.parseInt(userIdParam);

            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            // Force connection settings for this session
            session.doWork(new org.hibernate.jdbc.Work() {
                @Override
                public void execute(java.sql.Connection connection) throws java.sql.SQLException {
                    try {
                        connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_COMMITTED);
                        connection.setAutoCommit(false);
                        System.out.println("ExpenseListServlet DELETE - Connection isolation level: " + connection.getTransactionIsolation());
                    } catch (Exception e) {
                        System.err.println("Error setting connection properties: " + e.getMessage());
                    }
                }
            });

            // Clear session cache to ensure fresh data
            session.clear();

            // Find expense and verify ownership
            Criteria deleteCriteria = session.createCriteria(Expense.class);
            deleteCriteria.add(Restrictions.eq("id", expenseId));
            deleteCriteria.add(Restrictions.eq("users.id", userId));
            deleteCriteria.setCacheable(false); // Force fresh query
            Expense expense = (Expense) deleteCriteria.uniqueResult();

            if (expense == null) {
                System.out.println("Expense not found or no permission - ID: " + expenseId + ", UserId: " + userId);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Expense not found or you don't have permission to delete it");
                out.print(gson.toJson(errorResponse));
                return;
            }

            System.out.println("Found expense to delete: " + expense.getDescription());

            // Delete the expense
            session.delete(expense);
            
            // IMPORTANT: Flush the session to ensure the delete is executed immediately
            session.flush();
            
            transaction.commit();
            
            System.out.println("Expense deleted successfully from database");

            // Return success response
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Expense deleted successfully");
            successResponse.addProperty("deletedExpenseId", expenseId);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(successResponse));

        } catch (NumberFormatException e) {
            if (transaction != null) {
                transaction.rollback();
            }
            System.err.println("Invalid number format: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("message", "Invalid expense ID or user ID format");
            out.print(gson.toJson(errorResponse));
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }

            System.err.println("Error deleting expense: " + e.getMessage());
            e.printStackTrace();

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("message", "Internal server error: " + e.getMessage());
            out.print(gson.toJson(errorResponse));
        } finally {
            if (session != null) {
                session.close();
                System.out.println("Delete operation - Hibernate session closed");
            }
            out.flush();
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
