package com.myfinances;

import javax.swing.*;
import java.sql.Connection;
import java.time.LocalDate;


public interface IFinancePlugin {
    
    String getName();
    
    String getDescription();
    
    void execute(Connection dbConn, JFrame parentFrame, LocalDate startDate, LocalDate endDate);
}