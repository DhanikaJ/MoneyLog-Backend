package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import hibernate.Expense;
import hibernate.HibernateUtil;
import hibernate.Users;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

@WebServlet(name = "DashboardServlet", urlPatterns = {"/DashboardServlet"})
public class DashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        PrintWriter out = response.getWriter();
        Session session = null;

        try {
            // Get user ID from request parameter
            String userIdParam = request.getParameter("userId");
            if (userIdParam == null || userIdParam.trim().isEmpty()) {
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
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Invalid user ID format");
                out.print(gson.toJson(errorResponse));
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();
            
            // Force connection settings for this session
            session.doWork(new org.hibernate.jdbc.Work() {
                @Override
                public void execute(java.sql.Connection connection) throws java.sql.SQLException {
                    try {
                        connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_COMMITTED);
                        connection.setAutoCommit(false);
                        System.out.println("Dashboard - Connection isolation level: " + connection.getTransactionIsolation());
                    } catch (Exception e) {
                        System.err.println("Error setting connection properties: " + e.getMessage());
                    }
                }
            });
            
            // IMPORTANT: Clear session cache to ensure fresh data
            session.clear();

            // Verify user exists
            Criteria userCriteria = session.createCriteria(Users.class);
            userCriteria.add(Restrictions.eq("id", userId));
            Users user = (Users) userCriteria.uniqueResult();

            if (user == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "User not found");
                out.print(gson.toJson(errorResponse));
                return;
            }

            // Get total expenses and transaction count
            Criteria totalCriteria = session.createCriteria(Expense.class);
            totalCriteria.add(Restrictions.eq("users.id", userId));
            totalCriteria.setProjection(Projections.projectionList()
                    .add(Projections.sum("amount"))
                    .add(Projections.count("id")));
            
            // Force fresh query
            totalCriteria.setCacheable(false);
            
            Object[] totalResult = (Object[]) totalCriteria.uniqueResult();
            double totalExpenses = 0.0;
            long totalTransactions = 0L;
            
            if (totalResult != null) {
                // Handle Double type from your model
                if (totalResult[0] != null) {
                    if (totalResult[0] instanceof Double) {
                        totalExpenses = (Double) totalResult[0];
                    } else if (totalResult[0] instanceof BigDecimal) {
                        totalExpenses = ((BigDecimal) totalResult[0]).doubleValue();
                    }
                }
                
                if (totalResult[1] != null) {
                    totalTransactions = ((Long) totalResult[1]).longValue();
                }
            }

            // Get current month's total
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date monthStart = calendar.getTime();

            calendar.add(Calendar.MONTH, 1);
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            Date monthEnd = calendar.getTime();

            Criteria monthCriteria = session.createCriteria(Expense.class);
            monthCriteria.add(Restrictions.eq("users.id", userId));
            monthCriteria.add(Restrictions.between("dates", monthStart, monthEnd));
            monthCriteria.setProjection(Projections.sum("amount"));
            monthCriteria.setCacheable(false); // Force fresh query
            
            Object monthlyResult = monthCriteria.uniqueResult();
            double monthlyTotal = 0.0;
            
            if (monthlyResult != null) {
                if (monthlyResult instanceof Double) {
                    monthlyTotal = (Double) monthlyResult;
                } else if (monthlyResult instanceof BigDecimal) {
                    monthlyTotal = ((BigDecimal) monthlyResult).doubleValue();
                }
            }

            // Get recent expenses (latest 5)
            Criteria recentCriteria = session.createCriteria(Expense.class, "e");
            recentCriteria.createAlias("e.category", "c");
            recentCriteria.add(Restrictions.eq("e.users.id", userId));
            recentCriteria.addOrder(Order.desc("e.created_at"));
            recentCriteria.setMaxResults(5);
            recentCriteria.setCacheable(false); // Force fresh query
            
            List<Expense> recentExpenses = recentCriteria.list();

            // Get category totals
            Criteria categoryCriteria = session.createCriteria(Expense.class, "e");
            categoryCriteria.createAlias("e.category", "c");
            categoryCriteria.add(Restrictions.eq("e.users.id", userId));
            categoryCriteria.setProjection(Projections.projectionList()
                    .add(Projections.groupProperty("c.name"))
                    .add(Projections.sum("e.amount").as("totalAmount"))
                    .add(Projections.count("e.id")));
            categoryCriteria.addOrder(Order.desc("totalAmount"));
            categoryCriteria.setCacheable(false); // Force fresh query
            
            List<Object[]> categoryResults = categoryCriteria.list();

            // Build response
            JsonObject dashboardData = new JsonObject();
            dashboardData.addProperty("totalExpenses", totalExpenses);
            dashboardData.addProperty("totalTransactions", (int) totalTransactions);
            dashboardData.addProperty("monthlyTotal", monthlyTotal);

            // Convert recent expenses to JSON-friendly format
            List<Map<String, Object>> recentExpensesList = new ArrayList<>();
            for (Expense expense : recentExpenses) {
                Map<String, Object> expenseMap = new HashMap<>();
                expenseMap.put("id", expense.getId());
                expenseMap.put("amount", expense.getAmount());
                expenseMap.put("description", expense.getDescription());
                expenseMap.put("dates", expense.getDates().toString());
                expenseMap.put("createdAt", expense.getCreated_at().toString());
                expenseMap.put("updatedAt", expense.getUpdated_at().toString());
                
                Map<String, Object> categoryMap = new HashMap<>();
                categoryMap.put("id", expense.getCategory().getId());
                categoryMap.put("name", expense.getCategory().getName());
                expenseMap.put("category", categoryMap);
                
                recentExpensesList.add(expenseMap);
            }

            // Convert category totals to JSON-friendly format
            List<Map<String, Object>> categoryTotalsList = new ArrayList<>();
            for (Object[] categoryResult : categoryResults) {
                Map<String, Object> categoryMap = new HashMap<>();
                categoryMap.put("categoryName", (String) categoryResult[0]);
                
                // Handle Double type for category totals
                double categoryTotal = 0.0;
                if (categoryResult[1] != null) {
                    if (categoryResult[1] instanceof Double) {
                        categoryTotal = (Double) categoryResult[1];
                    } else if (categoryResult[1] instanceof BigDecimal) {
                        categoryTotal = ((BigDecimal) categoryResult[1]).doubleValue();
                    }
                }
                
                categoryMap.put("total", categoryTotal);
                categoryMap.put("count", categoryResult[2] != null ? ((Long) categoryResult[2]).intValue() : 0);
                categoryTotalsList.add(categoryMap);
            }

            // Create final response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("totalExpenses", totalExpenses);
            responseData.put("totalTransactions", (int) totalTransactions);
            responseData.put("monthlyTotal", monthlyTotal);
            responseData.put("recentExpenses", recentExpensesList);
            responseData.put("categoryTotals", categoryTotalsList);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(responseData));

        } catch (Exception e) {
            System.err.println("Error in DashboardServlet: " + e.getMessage());
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
}