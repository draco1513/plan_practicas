# ESFX TaskFlow Product Documentation

## Project Overview

ESFX TaskFlow is an internal ERP-like task management system developed by ESFX Technologies. It's designed to manage projects, tasks, calendar events, notifications, and time tracking for a team of users with different roles and permissions.

The project consists of:
- **Backend**: Java Spring Boot application (Maven, Java 21)
- **Frontend**: React + TypeScript + Bootstrap (Vite) 
- **Database**: PostgreSQL 16 (with Flyway migrations)
- **Message Queue**: Redis 7
- **Security**: JWT authentication with BCrypt password hashing

## Key Features

1. **User Management** - Role-based access control with different permissions for Administrators, Managers, Project Leaders, and Collaborators
2. **Project & Task Management** - Kanban-style task boards, project creation and management
3. **Calendar Integration** - Meeting scheduling and calendar views
4. **Notifications** - Email and in-app notifications via Redis queue
5. **Reporting** - Dashboard views for projects and time tracking
6. **Time Tracking** - Hours logging and reporting capabilities

## Target Users

- **Administrators**: Full system access, user management
- **Managers**: Project creation, editing, and team management
- **Project Leaders**: Task assignment, project editing
- **Collaborators**: Task completion, commenting, time tracking

## Technical Stack

### Backend
- Java 21 with Spring Boot 3.3
- PostgreSQL 16 with Flyway migrations
- Redis 7 for message queuing
- JWT authentication with BCrypt password hashing
- REST API following OpenAPI specification

### Frontend  
- React 18 + TypeScript
- Vite build tool
- Bootstrap 5.3 CSS framework
- Inter font family

## Architecture Principles

1. **Modular Design**: Each module has clear separation of concerns (access, projects, calendar, notifications, reports, timetracking)
2. **No Foreign Keys Between Schemas**: Modules communicate via services and events
3. **Event-Driven Communication**: Modules publish events that other modules can react to
4. **Interface-Based Design**: Services communicate through interfaces defined in common module
5. **Separation of Concerns**: Controllers (REST endpoints), Services (business logic), Repositories (data access), DTOs (data transfer objects)

## Development Approach

The system is being built in 3 phases:
1. **Week 7**: Technical design, wireframes, and style guide
2. **Weeks 8-9**: Implementation of backend and frontend modules in order
3. **Week 10**: Integration and MVP testing
4. **Week 11-12**: CI/CD pipeline implementation

## Current Status

The project is in the planning phase (Week 7). The design technical documentation has been created, and the implementation will begin in Week 8.

## Data Flow

1. User authentication via JWT tokens
2. API requests processed through Spring Boot controllers
3. Business logic handled by service layer
4. Data persistence via repositories with PostgreSQL
5. Notifications sent via Redis queue to email provider
6. Reports aggregated from multiple modules

## Security Considerations

- Passwords are hashed using BCrypt and never stored in plain text
- JWT tokens used for session management
- No sensitive data is logged or returned in responses
- Role-based access control prevents unauthorized actions