package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import hibernate.Category;
import hibernate.HibernateUtil;
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

@WebServlet(name = "CategoryServlet", urlPatterns = {"/CategoryServlet"})
public class CategoryServlet extends HttpServlet {

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
            session = HibernateUtil.getSessionFactory().openSession();

            // Get all categories ordered by name
            Criteria criteria = session.createCriteria(Category.class);
            criteria.addOrder(Order.asc("name"));
            List<Category> categories = criteria.list();

            // If no categories exist, create default ones
            if (categories.isEmpty()) {
                createDefaultCategories(session);
                // Fetch categories again after creating defaults
                criteria = session.createCriteria(Category.class);
                criteria.addOrder(Order.asc("name"));
                categories = criteria.list();
            }

            // Convert categories to JSON-friendly format
            List<Map<String, Object>> categoriesList = new ArrayList<>();
            for (Category category : categories) {
                Map<String, Object> categoryMap = new HashMap<>();
                categoryMap.put("id", category.getId());
                categoryMap.put("name", category.getName());
                categoriesList.add(categoryMap);
            }

            // Create response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Categories fetched successfully");
            responseData.put("categories", categoriesList);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(responseData));

        } catch (Exception e) {
            System.err.println("Error in CategoryServlet: " + e.getMessage());
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

    // Create default categories if none exist
    private void createDefaultCategories(Session session) {
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();

            String[] defaultCategories = {
                "Food & Dining",
                "Transportation",
                "Shopping",
                "Entertainment",
                "Bills & Utilities",
                "Healthcare",
                "Travel",
                "Education",
                "Personal Care",
                "Other"
            };

            for (String categoryName : defaultCategories) {
                Category category = new Category();
                category.setName(categoryName);
                session.save(category);
            }

            transaction.commit();
            System.out.println("Default categories created successfully");

        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            System.err.println("Error creating default categories: " + e.getMessage());
            e.printStackTrace();
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
