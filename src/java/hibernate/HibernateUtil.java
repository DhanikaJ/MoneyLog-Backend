package hibernate;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.util.Properties;

public class HibernateUtil {

    private static final SessionFactory sessionFactory;

    static {
        try {
            Configuration configuration = new Configuration().configure();

            // Override DB credentials from environment variables (never commit real passwords)
            String dbUrl = System.getenv("DB_URL");
            String dbUser = System.getenv("DB_USER");
            String dbPassword = System.getenv("DB_PASSWORD");

            Properties props = new Properties();
            if (dbUrl != null && !dbUrl.isEmpty()) {
                props.setProperty("hibernate.connection.url", dbUrl);
            }
            if (dbUser != null && !dbUser.isEmpty()) {
                props.setProperty("hibernate.connection.username", dbUser);
            }
            if (dbPassword != null && !dbPassword.isEmpty()) {
                props.setProperty("hibernate.connection.password", dbPassword);
            }

            if (!props.isEmpty()) {
                configuration.getProperties().putAll(props);
            }

            sessionFactory = configuration.buildSessionFactory();
        } catch (Throwable ex) {
            System.err.println("Initial SessionFactory creation failed." + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public static void shutdown() {
        getSessionFactory().close();
    }
}
