[English](26-real-world-application.md) | [Português](26-real-world-application.pt_BR.md)

# 26 — Real-World Application

> **Status: future (post 0.2 — `kof.web` + `kof_db` already cover the case without Spring)**
>
> This chapter shows how to build a complete application in Kof with Spring Boot.

## Task Manager today (without Spring)

The same API runs today with `kof.web` + `kof.orm` — one file, one `main()`:

```kf
entity Task {
    id: Long generated
    title: String
    description: String
    completed: Bool
}

main() {
    var db = db.connect("jdbc:h2:mem:tasks;DB_CLOSE_DELAY=-1")
    orm.create<Task>(db)

    var app = web.app()
    app.get("/tasks") {
        return json.encode(orm.all<Task>(db))
    }
    app.get("/tasks/:id") {
        var t = orm.find<Task>(db, param("id").toLong())
        if (t == null) {
            return status(404, "not found")
        }
        return json.encode(t)
    }
    app.post("/tasks") {
        var r = json.decode<CreateTaskRequest>(body())
        var t = orm.save(db, Task(0, r.title, r.description, false))
        return status(201, json.encode(t))
    }
    app.put("/tasks/:id/complete") {
        var t = orm.find<Task>(db, param("id").toLong())
        return json.encode(orm.save(db, Task(t.id, t.title, t.description, true)))
    }
    app.listen(8080)
}
```

```kf
record CreateTaskRequest(String title, String description)
```

- `kof serve tasks.kf` brings up the API; tests with the structured suite
  (`test "nome" { assert(...) }`) + `KofWebE2ETest`-style real sockets.
- The `entity` declares the schema in the language — the compiler knows the
  fields, types and constraints at compile-time (no reflection).

## The long-term vision: Task Manager with Spring

Let's build a simple task management API.

### Structure

```
src/main/kof/com/exemplo/tasks/
├── Task.kf
├── TaskRepository.kf
├── TaskService.kf
├── TaskController.kf
└── Application.kf
```

### Domain

```kf
record Task(
    UUID id,
    String title,
    String description,
    Bool completed,
    java.time.LocalDateTime createdAt
)
```

### Repository

```kf
interface TaskRepository extends CrudRepository<Task, UUID> {
    List<Task> findByCompletedFalse();
    List<Task> findByCompletedTrue();
}
```

### Service

```kf
@Service
class TaskService(TaskRepository repository) {

    Task create(String title, String description) {
        var task = new Task(
            UUID.randomUUID(),
            title,
            description,
            false,
            java.time.LocalDateTime.now()
        );
        return repository.save(task);
    }

    Task complete(UUID id) {
        var task = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("task not found"));
        var updated = new Task(task.id(), task.title(), task.description(), true, task.createdAt());
        return repository.save(updated);
    }

    List<Task> pendingTasks() {
        return repository.findByCompletedFalse();
    }
}
```

### Controller

```kf
@RestController
@RequestMapping("/tasks")
class TaskController(TaskService service) {

    @PostMapping
    Task create(@RequestBody CreateTaskRequest request) {
        return service.create(request.title(), request.description());
    }

    @PutMapping("/{id}/complete")
    Task complete(@PathVariable UUID id) {
        return service.complete(id);
    }

    @GetMapping("/pending")
    List<Task> pending() {
        return service.pendingTasks();
    }
}
```

### Request DTO

```kf
record CreateTaskRequest(String title, String description)
```

### Application

```kf
@SpringBootApplication
class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Tests

```kf
@SpringBootTest
class TaskServiceTest {
    @Autowired
    TaskService service;

    @Test
    void deveCriarTask() {
        var task = service.create("Estudar Kof", "ler documentação");
        assertFalse(task.completed());
        assertEquals("Estudar Kof", task.title());
    }

    @Test
    void deveCompletarTask() {
        var task = service.create("Tarefa", "descrição");
        var completed = service.complete(task.id());
        assertTrue(completed.completed());
    }
}
```

## Next step

[Best Practices →](27-best-practices.md)
