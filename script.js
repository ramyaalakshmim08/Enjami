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
// Real authentication must be handled by the backend.
const loginForm = document.getElementById("loginForm");

if (loginForm) {
loginForm.addEventListener("submit", function (event) {
event.preventDefault();


    const roleSelect = document.getElementById("userRole");
    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const message = document.getElementById("loginMessage");

    if (!roleSelect || !emailInput || !passwordInput) {
        return;
    }

    if (message) {
        message.textContent = "";
    }

    const role = roleSelect.value;

    const dashboards = {
        student: "dashboard.html",
        cr: "cr-dashboard.html",
        teacher: "teacher-dashboard.html",
        hod: "hod-dashboard.html"
    };

    if (!dashboards[role]) {
        if (message) {
            message.textContent = "Please select a valid role.";
        }
        return;
    }

    if (!emailInput.checkValidity() || !passwordInput.value) {
        if (message) {
            message.textContent = "Please enter a valid email and password.";
        }
        return;
    }

    window.location.href = dashboards[role];
});

}

// ===== Signup Form =====
// Registration is not enabled until backend integration.
const signupForm = document.getElementById("signupForm");

if (signupForm) {
signupForm.addEventListener("submit", function (event) {
event.preventDefault();


    const passwordInput = document.getElementById("signupPassword");
    const confirmPasswordInput = document.getElementById("confirmPassword");
    const message = document.getElementById("signupMessage");

    if (!passwordInput || !confirmPasswordInput || !message) {
        return;
    }

    message.style.color = "#dc2626";
    message.textContent = "";

    if (passwordInput.value !== confirmPasswordInput.value) {
        message.textContent = "Passwords do not match!";
        confirmPasswordInput.focus();
        return;
    }

    if (passwordInput.value.length < 8) {
        message.textContent = "Password must contain at least 8 characters.";
        passwordInput.focus();
        return;
    }

    message.style.color = "#b45309";
    message.textContent =
        "Registration is not active yet. Please try again after the backend is connected.";
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
