package java_codes;

import javax.swing.*;
import java.awt.*;

public class SchedulerGUI extends JFrame {
    private JLabel helloLabel;
    
    public SchedulerGUI() {
        initializeComponents();
        configureWindow();
    }
    
    private void initializeComponents() {
        helloLabel = new JLabel("Hello World", SwingConstants.CENTER);
        helloLabel.setFont(new Font("Arial", Font.BOLD, 24));
        helloLabel.setForeground(Color.BLUE);
        
        add(helloLabel);
    }
    
    private void configureWindow() {
        setTitle("Hello World GUI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 200);
        setLocationRelativeTo(null); 
    }
    
    public void showGUI() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setVisible(true);
            }
        });
    }
}