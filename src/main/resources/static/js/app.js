document.addEventListener("DOMContentLoaded", () => {
  const summary = document.querySelector("[data-error-summary]");
  if (summary) summary.focus();

  document.querySelectorAll("[data-password-toggle]").forEach((button) => {
    button.addEventListener("click", () => {
      const input = document.getElementById(button.getAttribute("aria-controls"));
      if (!input) return;
      const showing = input.type === "text";
      input.type = showing ? "password" : "text";
      button.textContent = showing ? "Hiện" : "Ẩn";
      button.setAttribute("aria-label", showing ? "Hiện mật khẩu" : "Ẩn mật khẩu");
    });
  });

  document.querySelectorAll("form[data-prevent-double-submit]").forEach((form) => {
    form.addEventListener("submit", () => {
      form.querySelectorAll("button[type='submit']").forEach((button) => {
        button.disabled = true;
        button.setAttribute("aria-busy", "true");
      });
    });
  });

  document.querySelectorAll("[data-supervisor-group]").forEach((group) => {
    const choices = [...group.querySelectorAll("input[type='checkbox']")];
    const updateChoices = () => {
      const selectedCount = choices.filter((choice) => choice.checked).length;
      choices.forEach((choice) => {
        choice.disabled = selectedCount >= 2 && !choice.checked;
      });
    };
    choices.forEach((choice) => choice.addEventListener("change", updateChoices));
    updateChoices();
  });

  document.querySelectorAll("[data-period-form]").forEach((form) => {
    const type = form.querySelector("[data-period-type]");
    const conditionalFields = [...form.querySelectorAll("[data-period-conditional]")];
    const today = form.dataset.today ?? "";
    const lecturerStart = form.querySelector("#lecturer-start");
    const lecturerEnd = form.querySelector("#lecturer-end");
    const studentStart = form.querySelector("#student-start");
    const studentEnd = form.querySelector("#student-end");

    const addDays = (value, days) => {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return "";
      const [year, month, day] = value.split("-").map(Number);
      const date = new Date(Date.UTC(year, month - 1, day + days));
      return date.toISOString().slice(0, 10);
    };
    const laterDate = (...values) => values.filter(Boolean).sort().at(-1) ?? "";
    const setMinimum = (input, value) => {
      if (input && value) input.min = value;
    };
    const updateScheduleMinimums = () => {
      setMinimum(lecturerStart, today);
      setMinimum(lecturerEnd, laterDate(today, lecturerStart?.value));
      setMinimum(studentStart, laterDate(today, addDays(lecturerEnd?.value ?? "", 1)));
      setMinimum(studentEnd, laterDate(today, studentStart?.value));
    };

    const updateConditionalFields = () => {
      const selected = type?.value ?? "";
      conditionalFields.forEach((container) => {
        const applies = container.dataset.periodConditional.split(",").includes(selected);
        const input = container.querySelector("input");
        container.hidden = !applies;
        if (input && input.dataset.locked !== "true") input.disabled = !applies;
      });
    };
    type?.addEventListener("change", updateConditionalFields);
    [lecturerStart, lecturerEnd, studentStart, studentEnd].forEach((input) => {
      input?.addEventListener("change", updateScheduleMinimums);
    });
    updateConditionalFields();
    updateScheduleMinimums();
  });

  const search = document.querySelector("[data-table-filter]");
  const status = document.querySelector("[data-status-filter]");
  const rows = [...document.querySelectorAll("[data-filter-table] tbody tr")];
  const filterRows = () => {
    const query = search?.value.trim().toLocaleLowerCase("vi") ?? "";
    const selectedStatus = status?.value ?? "";
    rows.forEach((row) => {
      const matchesText = row.textContent.toLocaleLowerCase("vi").includes(query);
      const matchesStatus = !selectedStatus || row.dataset.status === selectedStatus;
      row.hidden = !(matchesText && matchesStatus);
    });
  };
  search?.addEventListener("input", filterRows);
  status?.addEventListener("change", filterRows);
});
