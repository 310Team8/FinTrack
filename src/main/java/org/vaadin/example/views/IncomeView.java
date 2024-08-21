package org.vaadin.example.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.vaadin.example.MainLayout;
import org.vaadin.example.model.Income;
import org.vaadin.example.model.Note;
import org.vaadin.example.service.IncomeService;
import org.vaadin.example.service.NoteService;
import org.vaadin.example.service.SessionService;
import org.vaadin.example.service.UserService;

@Route(value = "income", layout = MainLayout.class)
public class IncomeView extends VerticalLayout {

    private final Grid<Income> grid = new Grid<>(Income.class);
    private final TextField sourceField = new TextField("Source");
    private final TextField amountField = new TextField("Amount");
    private final DatePicker datePicker = new DatePicker("Date");
    private final ComboBox<String> frequencyField = new ComboBox<>("Payment Frequency");

    private final IncomeService incomeService;
    private final NoteService noteService;
    private final SessionService sessionService;
    private final UserService userService;

    private H2 cardValue;
    private Div totalIncomeCard;
    private Div incomeSourcesCard;
    private Div notesSection;

    private Income selectedIncome; 

    public IncomeView(
            IncomeService incomeService,
            NoteService noteService,
            SessionService sessionService,
            UserService userService) {
        this.incomeService = incomeService;
        this.noteService = noteService;
        this.sessionService = sessionService;
        this.userService = userService;

        configureGrid();
        configureForm();

        Button addButton = new Button("Add/Update Income", event -> addOrUpdateIncome());
        Button deleteButton = new Button("Delete Income", event -> deleteIncome());

        HorizontalLayout formLayout =
                new HorizontalLayout(
                        sourceField, amountField, datePicker, frequencyField, addButton, deleteButton);
        formLayout.setWidthFull();
        formLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);

        
        cardValue = new H2("$ 0.00");
        totalIncomeCard = createDashboardCard("Forecasted Total Income for this Month", cardValue);

        notesSection = new Div();
        notesSection.addClassName("notes-section");

        TextField notesInput = new TextField();
        notesInput.setPlaceholder("Add a note and press Enter");
        notesInput.setWidthFull();

        notesInput.addKeyPressListener(
                event -> {
                    String note = notesInput.getValue();
                    if (!note.trim().isEmpty()) {
                        addNoteToDatabase(note);
                        addNoteToCard(null, note);
                        notesInput.clear();
                    }
                });

        VerticalLayout notesLayout = new VerticalLayout();
        notesLayout.setWidthFull();
        notesLayout.add(new H3("Notes about Income"), notesSection, notesInput);

        totalIncomeCard.add(notesLayout);

        incomeSourcesCard = createIncomeSourcesCard("Income Sources Breakdown", new Div());

        H1 logo = new H1("Income");

        HorizontalLayout cardsAndGridLayout = new HorizontalLayout(totalIncomeCard, incomeSourcesCard);
        cardsAndGridLayout.setWidthFull();
        cardsAndGridLayout.setFlexGrow(1, totalIncomeCard);
        cardsAndGridLayout.setFlexGrow(1, incomeSourcesCard);
        cardsAndGridLayout.setAlignItems(FlexComponent.Alignment.START);

        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.add(logo, cardsAndGridLayout, formLayout, grid);
        mainLayout.setSpacing(false);

        add(mainLayout);

        listIncomes();
        listNotes();
        updateTotalIncome();
    }

    private void configureGrid() {
        grid.setColumns("source", "amount", "date", "paymentFrequency");
        grid.addComponentColumn(income -> {
            Button editButton = new Button("Edit");
            editButton.addClickListener(event -> editIncome(income));
            return editButton;
        }).setHeader("Actions");
    
        grid.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null) {
                editIncome(event.getValue());
            }
        });


        grid.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null) {
                editIncome(event.getValue());
            }
        });
    }

    private void configureForm() {
        sourceField.setPlaceholder("e.g., Salary");
        amountField.setPlaceholder("e.g., 1000.00");
        datePicker.setPlaceholder("Select a date");

        frequencyField.setItems("Weekly", "Biweekly", "Monthly");
        frequencyField.setPlaceholder("Select payment frequency");
    }

    private void listIncomes() {
        Long userId = sessionService.getLoggedInUserId();
        List<Income> incomes = incomeService.getIncomesByUserId(userId);
        grid.setItems(incomes);
    }

    private void addOrUpdateIncome() {
        String source = sourceField.getValue();
        String amountText = amountField.getValue();
        LocalDate date = datePicker.getValue();
        String paymentFrequency = frequencyField.getValue();
    
        if (source.trim().isEmpty() || amountText.trim().isEmpty() || date == null || paymentFrequency == null) {
            Notification.show("Please fill in all fields: Source, Amount, Date, and Payment Frequency.");
            return;
        }
    
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Amount must be greater than 0.");
                return;
            }
        } catch (NumberFormatException e) {
            Notification.show("Amount must be a valid number.");
            return;
        }
    
        if (selectedIncome == null) {
            addIncome(source, amount, date, paymentFrequency);
        } else {
            updateIncome(source, amount, date, paymentFrequency);
        }
    }
    
    private void addIncome(String source, BigDecimal amount, LocalDate date, String paymentFrequency) {
        Income income = new Income();
        income.setSource(source);
        income.setAmount(amount);
        income.setDate(java.sql.Date.valueOf(date)); // Convert LocalDate to java.sql.Date
        income.setPaymentFrequency(paymentFrequency);
        income.setUser(userService.findUserById(sessionService.getLoggedInUserId()));
    
        incomeService.addIncome(income);
        Notification.show("Income added successfully");
        listIncomes();
        updateTotalIncome();
        clearForm();
    }
    
    private void updateIncome(String source, BigDecimal amount, LocalDate date, String paymentFrequency) {
        if (selectedIncome != null) {
            selectedIncome.setSource(source);
            selectedIncome.setAmount(amount);
            selectedIncome.setDate(java.sql.Date.valueOf(date));
            selectedIncome.setPaymentFrequency(paymentFrequency);
    
            incomeService.updateIncome(selectedIncome);
            Notification.show("Income updated successfully");
            listIncomes();
            updateTotalIncome();
            clearForm();
            selectedIncome = null;
        }
    }

    private void editIncome(Income income) {
        selectedIncome = income;

        sourceField.setValue(income.getSource());
        amountField.setValue(income.getAmount().toString());
        datePicker.setValue(((Date) income.getDate()).toLocalDate());
        frequencyField.setValue(income.getPaymentFrequency());
    }

    private void deleteIncome() {
        Income selectedIncome = grid.asSingleSelect().getValue();
        if (selectedIncome != null) {
            incomeService.deleteIncome(selectedIncome.getId());
            Notification.show("Income deleted successfully");
            listIncomes();
            updateTotalIncome();
        } else {
            Notification.show("Please select an income to delete");
        }
    }

    private void clearForm() {
        sourceField.clear();
        amountField.clear();
        datePicker.clear();
        frequencyField.clear();
        selectedIncome = null;
    }

    private Div createDashboardCard(String title, H2 valueComponent) {
        Div card = new Div();
        card.addClassName("dashboard-card");

        H3 cardTitle = new H3(title);
        cardTitle.addClassName("card-title");

        valueComponent.addClassName("card-value");

        card.add(cardTitle, valueComponent);
        return card;
    }

    private Div createIncomeSourcesCard(String title, Div content) {
        Div card = new Div();
        card.addClassName("dashboard-card");

        H2 cardTitle = new H2(title);
        cardTitle.addClassName("card-title");

        content.addClassName("card-content");

        card.add(cardTitle, content);
        return card;
    }

    private void updateTotalIncome() {
        Long userId = sessionService.getLoggedInUserId();
        List<Income> incomes = incomeService.getIncomesByUserId(userId);
        BigDecimal totalIncome =
                incomes.stream().map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        cardValue.setText("$ " + totalIncome.toString());

        updateIncomeSourcesCard(incomes, totalIncome);
    }

    private void updateIncomeSourcesCard(List<Income> incomes, BigDecimal totalIncome) {
        Map<String, BigDecimal> sourcePercentages =
                incomes.stream()
                        .collect(
                                Collectors.groupingBy(
                                        Income::getSource,
                                        Collectors.reducing(BigDecimal.ZERO, Income::getAmount, BigDecimal::add)));
    
        Div content = new Div();
    
        // Add the header only if it hasn't been added yet
        if (content
                .getElement()
                .getChildren()
                .noneMatch(child -> child.getClass().equals("card-title"))) {
            H2 cardTitle = new H2("Income Stream Overview");
            cardTitle.addClassName("card-title");
            content.add(cardTitle);
        }
    
        sourcePercentages.forEach(
                (source, amount) -> {
                    @SuppressWarnings("deprecation")
                    BigDecimal percentage =
                            amount
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(totalIncome, 2, BigDecimal.ROUND_HALF_UP);
    
                    Div sourceContainer = new Div();
                    sourceContainer.addClassName("source-container");
    
                    Paragraph sourceParagraph = new Paragraph();
                    sourceParagraph.getElement().setText(source);
                    sourceParagraph.getElement().getStyle().set("font-weight", "bold");
                    sourceContainer.add(sourceParagraph);
    
                    String amountText = "$ " + amount.toString();
                    String percentageText = percentage.toString() + "% of Total Income";
                    Paragraph detailsParagraph = new Paragraph(amountText + " (" + percentageText + ")");
                    sourceContainer.add(detailsParagraph);
    
                    content.add(sourceContainer);
                });
    
        incomeSourcesCard.removeAll();
        incomeSourcesCard.add(content);
    }
    

    private void listNotes() {
        Long userId = sessionService.getLoggedInUserId();
        List<Note> notes = noteService.getNotesByUserId(userId);

        for (Note note : notes) {
            addNoteToCard(note.getId(), note.getContent());
        }
    }

    private void addNoteToCard(Long noteId, String note) {
        HorizontalLayout noteLayout = new HorizontalLayout();
        noteLayout.setWidthFull();
        noteLayout.setAlignItems(FlexComponent.Alignment.CENTER);
    
        Div noteContainer = new Div();
        noteContainer.getStyle().set("width", "100%");
        noteContainer.getStyle().set("overflow-wrap", "break-word");
        noteContainer.getStyle().set("word-wrap", "break-word");
        noteContainer.getStyle().set("hyphens", "auto");
        noteContainer.getStyle().set("margin-bottom", "10px");
    
        Paragraph noteParagraph = new Paragraph("• " + note);
        noteParagraph.getStyle().set("margin", "0");
        noteParagraph.getStyle().set("overflow-wrap", "break-word");
        noteParagraph.getStyle().set("word-wrap", "break-word");
    
        noteContainer.add(noteParagraph);
    
        Button deleteButton = new Button("Delete");
        deleteButton.addClickListener(
            event -> {
              noteService.deleteNoteById(noteId);
              notesSection.remove(noteLayout);
            });
    
        noteLayout.add(noteContainer, deleteButton);
        notesSection.add(noteLayout);
      }
    

    private void addNoteToDatabase(String noteContent) {
        Note note = new Note();
        note.setContent(noteContent);
        note.setUserId(sessionService.getLoggedInUserId());
        noteService.addNote(note);
    }
}
