package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hibernate.Category;
import hibernate.Expense;
import hibernate.HibernateUtil;
import hibernate.Users;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;

@WebServlet(name = "ExpenseServlet", urlPatterns = {"/ExpenseServlet"})
public class ExpenseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
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
            System.out.println("ExpenseServlet doPost called");
            
            // Read JSON from request body
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }

            String jsonString = jsonBuffer.toString();
            System.out.println("Received expense data: " + jsonString);

            if (jsonString.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Request body is empty");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Parse JSON
            JsonObject expenseData = JsonParser.parseString(jsonString).getAsJsonObject();

            // Extract expense data
            double amount = expenseData.has("amount") ? expenseData.get("amount").getAsDouble() : 0.0;
            int categoryId = expenseData.has("categoryId") ? expenseData.get("categoryId").getAsInt() : 0;
            String description = expenseData.has("description") ? expenseData.get("description").getAsString().trim() : "";
            String dateString = expenseData.has("dates") ? expenseData.get("dates").getAsString() : "";
            int userId = expenseData.has("userId") ? expenseData.get("userId").getAsInt() : 0;

            // Validate input
            if (amount <= 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Amount must be greater than 0");
                out.print(gson.toJson(errorResponse));
                return;
            }

            if (amount > 999999.99) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Amount cannot exceed Rs. 999,999.99");
                out.print(gson.toJson(errorResponse));
                return;
            }

            if (categoryId <= 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Valid category is required");
                out.print(gson.toJson(errorResponse));
                return;
            }

            if (description.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Description is required");
                out.print(gson.toJson(errorResponse));
                return;
            }

            if (description.length() > 255) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Description cannot exceed 255 characters");
                out.print(gson.toJson(errorResponse));
                return;
            }

            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Valid user ID is required");
                out.print(gson.toJson(errorResponse));
                return;
            }

            if (dateString.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Date is required");
                out.print(gson.toJson(errorResponse));
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            // Force connection settings for this session
            session.doWork(new org.hibernate.jdbc.Work() {
                @Override
                public void execute(java.sql.Connection connection) throws java.sql.SQLException {
                    try {
                        connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_COMMITTED);
                        connection.setAutoCommit(false);
                        System.out.println("ExpenseServlet DELETE - Connection isolation level: " + connection.getTransactionIsolation());
                    } catch (Exception e) {
                        System.err.println("Error setting connection properties: " + e.getMessage());
                    }
                }
            });

            // Clear session cache to ensure fresh data
            session.clear();

            // Verify user exists
            Criteria userCriteria = session.createCriteria(Users.class);
            userCriteria.add(Restrictions.eq("id", userId));
            userCriteria.setCacheable(false);
            Users user = (Users) userCriteria.uniqueResult();

            if (user == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "User not found");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Verify category exists
            Criteria categoryCriteria = session.createCriteria(Category.class);
            categoryCriteria.add(Restrictions.eq("id", categoryId));
            categoryCriteria.setCacheable(false);
            Category category = (Category) categoryCriteria.uniqueResult();

            if (category == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Category not found");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Parse date
            Date expenseDate;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                expenseDate = sdf.parse(dateString);
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Invalid date format. Use YYYY-MM-DD");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Create new expense
            Expense newExpense = new Expense();
            newExpense.setAmount(amount);
            newExpense.setCategory(category);
            newExpense.setDescription(description);
            newExpense.setDates(expenseDate);
            newExpense.setUsers(user);
            newExpense.setCreated_at(new Date());
            newExpense.setUpdated_at(new Date());

            // Save expense to database
            session.save(newExpense);
            
            // IMPORTANT: Flush the session to ensure the save is executed immediately
            session.flush();
            
            transaction.commit();
            
            System.out.println("Expense saved successfully with ID: " + newExpense.getId());

            // Return success response
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Expense added successfully");
            successResponse.addProperty("expenseId", newExpense.getId());
            successResponse.addProperty("amount", newExpense.getAmount());
            successResponse.addProperty("description", newExpense.getDescription());
            successResponse.addProperty("categoryName", category.getName());
            successResponse.addProperty("date", expenseDate.toString());

            response.setStatus(HttpServletResponse.SC_CREATED);
            out.print(gson.toJson(successResponse));

        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
                System.out.println("Transaction rolled back due to error");
            }

            System.err.println("Error in ExpenseServlet: " + e.getMessage());
            e.printStackTrace();

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("message", "Internal server error: " + e.getMessage());
            out.print(gson.toJson(errorResponse));
        } finally {
            if (session != null) {
                session.close();
                System.out.println("Add expense - Hibernate session closed");
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
            System.out.println("ExpenseServlet doDelete called");
            
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

            // Clear session cache to ensure fresh data
            session.clear();

            // Find expense and verify ownership
            Criteria expenseCriteria = session.createCriteria(Expense.class);
            expenseCriteria.add(Restrictions.eq("id", expenseId));
            expenseCriteria.add(Restrictions.eq("users.id", userId));
            expenseCriteria.setCacheable(false); // Force fresh query
            Expense expense = (Expense) expenseCriteria.uniqueResult();

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
                System.out.println("Delete transaction rolled back due to error");
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
                System.out.println("Delete expense - Hibernate session closed");
            }
            out.flush();
        }
    }
}
