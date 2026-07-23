package com.sovon9.uaa_resource_server.controller;

import com.sovon9.uaa_resource_server.entity.Employee;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class TestController {

    @GetMapping("/test")
    public String getData()
    {
        return "Successfully accessed site using Oauth2 public key validation";
    }

    /**
     * - Add an employee     POST
     * - Update an employee  PUT
     * - Delete an employee  DELETE
     * - Search for employee (employee id)
     * - Search for employee (skill, location, joblevel)
     */
    private List<Employee> employees;
    public TestController()
    {
        employees = new ArrayList<>();
        Employee e1 = new Employee(1L, "sovon");
        Employee e2 = new Employee(2L, "sougata");
        Employee e3 = new Employee(3L, "lisha");
        employees.add(e1);
        employees.add(e2);
        employees.add(e3);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/employee/{id}")
    public Employee getEmployee(@PathVariable Long id)
    {
        return employees.stream().filter(e->e.getId()==id).findFirst().orElse(null);
    }

    @PostMapping("/employee")
    public Employee saveEmployee(@RequestBody Employee employee)
    {
        employees.add(employee);
        return employee;
    }

    @PutMapping("/employee/{id}")
    public Employee updateEmployee(@PathVariable Long id, @RequestBody Employee employee)
    {
        int i=0;
        for(Employee emp:employees)
        {
            if(emp.getId()==id)
            {
                employees.set(i, employee);
                return employee;
            }
            i++;
        }
        return null;
    }

    @DeleteMapping("/employee/{id}")
    public boolean removeEmployee(@PathVariable Long id)
    {
        return employees.remove(employees.stream().filter(e -> e.getId() == id).findFirst().orElse(null));
    }

}
