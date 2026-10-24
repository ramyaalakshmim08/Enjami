// ===== Show / Hide Password =====
document.querySelectorAll(".password-toggle").forEach(function (button) {
button.addEventListener("click", function () {
const input = document.getElementById(button.dataset.target);


    if (!input) return;

    const isHidden = input.type === "password";
    input.type = isHidden ? "text" : "password";
    button.textContent = isHidden ? "Hide" : "Show";
});


});

// ===== Role-Based Login =====
// Frontend demonstration only.

const API_BASE = "http://localhost:8080/api";

// ===== Role-Based Login =====
const loginForm = document.getElementById("loginForm");

if (loginForm) {
    loginForm.addEventListener("submit", async function (event) {
        event.preventDefault();

        const roleSelect = document.getElementById("userRole");
        const emailInput = document.getElementById("email");
        const passwordInput = document.getElementById("password");
        const message = document.getElementById("loginMessage");

        if (!roleSelect || !emailInput || !passwordInput || !message) return;

        message.textContent = "Signing in...";
        message.style.color = "#b45309";

        try {
            const response = await fetch(`${API_BASE}/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify({
                    email: emailInput.value.trim(),
                    password: passwordInput.value
                })
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.message || "Login failed.");
            }

            // The backend's assigned role, not the selected role, is authoritative.
            const role = String(data.user?.role || "").toLowerCase();

            const dashboards = {
                student: "dashboard.html",
                cr: "cr-dashboard.html",
                teacher: "teacher-dashboard.html",
                hod: "hod-dashboard.html"
            };

            if (!dashboards[role]) {
                throw new Error("Your account has no supported dashboard role.");
            }

            if (roleSelect.value.toLowerCase() !== role) {
                throw new Error("The selected role does not match your account role.");
            }

            window.location.href = dashboards[role];
        } catch (error) {
            message.style.color = "#dc2626";
            message.textContent =
                error.message === "Failed to fetch"
                    ? "Cannot connect to the backend. Check that the Java server is running."
                    : error.message;
        }
    });
}

// ===== Signup Form =====
const signupForm = document.getElementById("signupForm");

if (signupForm) {
    signupForm.addEventListener("submit", async function (event) {
        event.preventDefault();

        const passwordInput = document.getElementById("signupPassword");
        const confirmPasswordInput = document.getElementById("confirmPassword");
        const message = document.getElementById("signupMessage");

        const usernameInput = document.getElementById("fullName");
const emailInput = document.getElementById("signupEmail");

        if (!passwordInput || !confirmPasswordInput || !message ||
            !usernameInput || !emailInput) {
            if (message) {
                message.textContent =
                    "A required signup field is missing. Check the form field IDs.";
            }
            return;
        }

        message.style.color = "#dc2626";
        message.textContent = "";

        if (passwordInput.value !== confirmPasswordInput.value) {
            message.textContent = "Passwords do not match.";
            confirmPasswordInput.focus();
            return;
        }

        if (passwordInput.value.length < 8) {
            message.textContent = "Password must contain at least 8 characters.";
            passwordInput.focus();
            return;
        }

        message.textContent = "Creating your account...";
        message.style.color = "#b45309";

        try {
            const response = await fetch(`${API_BASE}/register`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    username: usernameInput.value.trim(),
                    email: emailInput.value.trim(),
                    password: passwordInput.value
                })
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.message || "Registration failed.");
            }

            message.style.color = "#15803d";
            message.textContent =
                data.message || "Registration successful. You can now log in.";
        } catch (error) {
            message.style.color = "#dc2626";
            message.textContent =
                error.message === "Failed to fetch"
                    ? "Cannot connect to the backend. Check that the Java server is running."
                    : error.message;
        }
    });
}


// ===== ENJAMI Student Task List =====
(function () {
const taskForm = document.getElementById("taskForm");
const taskInput = document.getElementById("taskInput");
const taskList = document.getElementById("taskList");
const taskEmpty = document.getElementById("taskEmpty");


if (!taskForm || !taskInput || !taskList || !taskEmpty) {
    return;
}

const STORAGE_KEY = "enjamiTasks";
let tasks = [];

try {
    const savedTasks = localStorage.getItem(STORAGE_KEY);
    tasks = savedTasks ? JSON.parse(savedTasks) : [];

    if (!Array.isArray(tasks)) {
        tasks = [];
    }
} catch (error) {
    tasks = [];
}

function saveTasks() {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
    } catch (error) {
        console.error("Unable to save tasks:", error);
    }
}

function renderTasks() {
    taskList.innerHTML = "";

    tasks.forEach(function (task) {
        const item = document.createElement("li");
        item.className = "task-item";

        if (task.completed) {
            item.classList.add("completed");
        }

        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.checked = Boolean(task.completed);
        checkbox.setAttribute("aria-label", "Mark task complete");

        checkbox.addEventListener("change", function () {
            task.completed = checkbox.checked;
            saveTasks();
            renderTasks();
        });

        const text = document.createElement("span");
        text.textContent = task.text;

        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "delete-task";
        deleteButton.textContent = "Delete";

        deleteButton.addEventListener("click", function () {
            tasks = tasks.filter(function (existingTask) {
                return existingTask.id !== task.id;
            });

            saveTasks();
            renderTasks();
        });

        item.appendChild(checkbox);
        item.appendChild(text);
        item.appendChild(deleteButton);
        taskList.appendChild(item);
    });

    taskEmpty.hidden = tasks.length !== 0;
}

taskForm.addEventListener("submit", function (event) {
    event.preventDefault();

    const text = taskInput.value.trim();

    if (!text) {
        return;
    }

    tasks.push({
        id: Date.now() + Math.random(),
        text: text,
        completed: false
    });

    saveTasks();
    renderTasks();

    taskInput.value = "";
    taskInput.focus();
});

renderTasks();


})();
