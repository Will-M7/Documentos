package com.ejemplo.crudpersona.studio;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.util.Animations;
import com.ejemplo.crudpersona.studio.DocumentStore.Kind;
import com.ejemplo.crudpersona.studio.DocumentStore.Template;
import com.ejemplo.crudpersona.studio.DocumentStore.Variable;
import com.ejemplo.crudpersona.studio.DocumentStore.Definition;
import com.ejemplo.crudpersona.studio.DocumentStore.TemplateGroup;
import com.ejemplo.crudpersona.studio.StudioData.Contact;
import com.ejemplo.crudpersona.studio.StudioData.History;
import com.ejemplo.crudpersona.studio.StudioData.Mapping;
import com.ejemplo.crudpersona.studio.StudioData.ApiBinding;
import com.ejemplo.crudpersona.studio.StudioData.ContactBinding;
import com.ejemplo.crudpersona.studio.StudioData.ApiContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;
import netscape.javascript.JSObject;
import javafx.stage.*;
import javafx.util.Duration;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Desktop;
import java.util.*;
import java.util.regex.*;
import java.time.LocalDate;

/** Offline desktop workflow: template editor, document catalog and generator. */
public final class StudioWindow {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*)}}");
    private static final List<String> TYPES = VariableInput.TYPES;
    private final Stage stage;
    private final DocumentStore store;
    private final StudioData data;
    private final ApiCredentialStore credentials;
    private final LookupRouter lookupRouter;
    private final StudioTransfer transfer;
    private final DocumentActions documentActions;
    private final ObjectMapper json=new ObjectMapper();
    private final BorderPane root = new BorderPane();
    private final StackPane workspace = new StackPane();
    private final Label status = new Label("Datos locales · Sin conexión requerida");
    private final ToggleGroup menu = new ToggleGroup();
    private final List<ToggleButton> navigation = new ArrayList<>();
    private final List<Node> menuTexts = new ArrayList<>();
    private VBox sidebar;
    private VBox brandTexts;
    private Region indicator;
    private Button themeButton, collapseButton;
    private ToggleButton generateNavigation;
    private boolean darkTheme, collapsed;
    private final ComboBox<Template> templateChoice = new ComboBox<>();
    private BorderPane libraryPage;
    private final ListView<Template> libraryList=new ListView<>();
    private final ListView<TemplateGroup> groupList=new ListView<>();
    private final TextField librarySearch=new TextField();
    private final ComboBox<String> librarySort=new ComboBox<>();
    private final Label libraryGroups=new Label("Sin grupos");
    private Button addToGroup,removeFromGroup;
    private Map<Long,List<Long>> libraryMemberships=Map.of();
    private Map<Long,String> libraryGroupNames=Map.of(),libraryModifiedDates=Map.of();
    private boolean refreshingLibrary;
    private final ListView<Definition> variableList = new ListView<>();
    private final VBox paper = new VBox(10);
    private final ScrollPane documentScroll = new ScrollPane();
    private final WebView richEditor = new WebView();
    private ContinuousDocxEditor richModel;
    private boolean editorReady;
    private final EditorBridge editorBridge=new EditorBridge();
    private double editorLeft=72,editorRight=72,editorTop=72,editorBottom=72;
    private final Label pageIndicator = new Label("Página 1");
    private final ComboBox<String> zoomChoice = new ComboBox<>();
    private final TextField paletteSearch = new TextField();
    private VBox palettePanel;
    private Button paletteToggle;
    private TabPane editorTabs;
    private HBox tableTools;
    private HBox imageTools;
    private final Scale paperScale = new Scale(1,1);
    private double pageWidth = 794, pageHeight = 1123;
    private String zoomMode = "Ancho";
    private boolean readingPageSize;
    private final TextField templateName = new TextField();
    private BorderPane definitionsPage;
    private final ListView<Definition> definitionList=new ListView<>();
    private final TextField definitionSearch=new TextField(),definitionKey=new TextField(),definitionLabel=new TextField(),definitionDefault=new TextField();
    private final ComboBox<String> definitionType=new ComboBox<>();
    private final CheckBox definitionApiEnabled=new CheckBox("Vincular con API");
    private final ComboBox<String> definitionApiService=new ComboBox<>(),definitionApiField=new ComboBox<>();
    private final Label definitionApiPath=new Label("Consulta automática cuando haya conexión · Ingreso manual disponible");
    private VBox definitionApiOptions;
    private Definition selectedDefinition;
    private final ComboBox<String> fontChoice = new ComboBox<>();
    private final ComboBox<String> paperChoice = new ComboBox<>();
    private Template currentTemplate;
    private boolean updatingTemplates;
    private XWPFDocument document;
    private boolean dirty;
    private XWPFRun selectedRun;
    private XWPFParagraph selectedParagraph;
    private TextField selectedText;
    private XWPFRun dragSourceRun;
    private String dragSourceToken;
    private int dragSourceOffset;
    private XWPFRun dragTargetRun;
    private int dragTargetOffset;

    private final ListView<Kind> kindList = new ListView<>();
    private final TextField kindSearch = new TextField(), kindTitle = new TextField(), kindCategory = new TextField();
    private final TextArea kindDescription = new TextArea();
    private final ComboBox<Template> kindTemplate = new ComboBox<>();
    private final CheckBox kindActive = new CheckBox("Activo");
    private final ListView<Variable> fieldList = new ListView<>();
    private final ComboBox<Variable> addFieldChoice = new ComboBox<>();
    private final CheckBox fieldRequired=new CheckBox("Obligatorio en este documento");
    private final Map<String,Boolean> kindRequiredChoices=new HashMap<>();
    private final TextField fieldDefault=new TextField();
    private final Map<String,String> kindDefaultChoices=new HashMap<>();
    private boolean loadingFieldRequired;
    private final TextField bindingGroup=new TextField();
    private final ComboBox<String> bindingKind=new ComboBox<>(),bindingField=new ComboBox<>();
    private final ComboBox<Variable> bindingVariable=new ComboBox<>();
    private final ListView<ContactBinding> bindingList=new ListView<>();
    private Kind currentKind;

    private final ComboBox<Template> generateTemplate = new ComboBox<>();
    private boolean refreshingGenerator;
    private final Map<String,PauseTransition> lookupDelays=new HashMap<>();
    private final ComboBox<TemplateGroup> generateGroup=new ComboBox<>();
    private final TextField generateSearch=new TextField();
    private final VBox generatedForm = new VBox(15);
    private final Map<String, Control> inputs = new LinkedHashMap<>();
    private final Map<String,Variable> formVariables=new LinkedHashMap<>();
    private final Set<String> manuallyEdited=new HashSet<>();
    private boolean applyingApi;
    private final Map<String,String> localValueOverrides=new HashMap<>();
    private final VBox preview = new VBox(8);
    private byte[] generatedBytes;
    private byte[] generatedPdf;
    private Map<String,String> generatedValues=Map.of();
    private long generatedHistoryId;
    private byte[] historicalTemplate;
    private String contactSaveSummary="";
    private BorderPane contactsPage,historyPage,apiPage,settingsPage;
    private final ListView<Contact> contactList=new ListView<>();
    private final TextField contactSearch=new TextField(),contactGiven=new TextField(),contactFamily=new TextField(),contactDocType=new TextField(),contactNumber=new TextField(),contactAddress=new TextField();
    private final TextArea contactRepresentatives=new TextArea(),contactExtras=new TextArea();
    private final ComboBox<String> contactKind=new ComboBox<>();
    private Contact selectedContact;
    private final PasswordField apiToken=new PasswordField();
    private final ListView<ApiCredentialStore.Credential> credentialList=new ListView<>();
    private final TextField credentialName=new TextField();
    private final ComboBox<String> credentialProvider=new ComboBox<>();
    private final CheckBox credentialEnabled=new CheckBox("Habilitada");
    private final Label credentialStats=new Label("Sin credencial seleccionada");
    private final ComboBox<String> apiType=new ComboBox<>();
    private final TextField apiNumber=new TextField();
    private final Label apiState=new Label("Sin consulta");
    private boolean apiQueryRunning;
    private final VBox apiResultBox=new VBox(8);
    private final ComboBox<Template> mapTemplate=new ComboBox<>();
    private final ComboBox<Variable> mapSource=new ComboBox<>(),mapTarget=new ComboBox<>();
    private final ComboBox<String> mapApiField=new ComboBox<>();
    private final ListView<Mapping> mappingList=new ListView<>();
    private final ListView<History> historyList=new ListView<>();
    private final TextField historySearch=new TextField();
    private final DatePicker historyDate=new DatePicker();
    private final ComboBox<String> importConflict=new ComboBox<>(),exportScope=new ComboBox<>();
    private final ComboBox<Template> exportTemplate=new ComboBox<>();
    private final ComboBox<TemplateGroup> exportGroup=new ComboBox<>();
    private final Label apiSaved=new Label();

    public StudioWindow(Stage stage, DocumentStore store) throws Exception {
        this(stage,store,null);
    }
    StudioWindow(Stage stage, DocumentStore store,LookupRouter router) throws Exception {
        this(stage,store,router,new SystemDocumentActions());
    }
    StudioWindow(Stage stage,DocumentStore store,LookupRouter router,DocumentActions documentActions)throws Exception{
        this.stage=stage; this.store=store;
        this.documentActions=documentActions;
        data=new StudioData(store.directory());credentials=new ApiCredentialStore(store.directory());lookupRouter=router==null?new LookupRouter(credentials,List.of(new FactilizaProvider(),new ApisPeruProvider())):router;transfer=new StudioTransfer(store,data);
        buildShell();
        buildDefinitions();
        buildEditor();
        buildLibrary();
        buildGenerator();
        buildContacts();buildHistory();buildApi();buildSettings();
        menu.selectToggle(generateNavigation);
        Platform.runLater(() -> moveIndicator(generateNavigation));
    }
    public Parent root() { return root; }

    interface DocumentActions {
        void open(Path document)throws Exception;
        void print(Path document)throws Exception;
    }
    private static final class SystemDocumentActions implements DocumentActions {
        @Override public void open(Path document)throws Exception{
            try{if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.OPEN)){Desktop.getDesktop().open(document.toFile());return;}}catch(IOException|UnsupportedOperationException ignored){}
            String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
            ProcessBuilder command;
            if(os.contains("win"))command=new ProcessBuilder("cmd","/c","start","",document.toAbsolutePath().toString());
            else if(os.contains("linux"))command=new ProcessBuilder("xdg-open",document.toAbsolutePath().toString());
            else throw new IOException("No hay un procesador de texto disponible para abrir el DOCX.");
            Process process=command.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(process.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)&&process.exitValue()!=0)throw new IOException("No se pudo abrir el DOCX en el procesador de texto del sistema.");
        }
        @Override public void print(Path document)throws Exception{
            try{if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.PRINT)){Desktop.getDesktop().print(document.toFile());return;}}catch(IOException|UnsupportedOperationException ignored){}
            String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);ProcessBuilder command;
            if(os.contains("linux"))command=new ProcessBuilder("lp",document.toAbsolutePath().toString());
            else if(os.contains("win")){
                command=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command","Start-Process -FilePath $env:STUDIO_PRINT_FILE -Verb Print -ErrorAction Stop");
                command.environment().put("STUDIO_PRINT_FILE",document.toAbsolutePath().toString());
            }else throw new IOException("La impresión directa no está disponible en este sistema.");
            Process process=command.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){process.destroy();throw new IOException("El sistema de impresión no respondió.");}
            if(process.exitValue()!=0)throw new IOException("No se pudo enviar el PDF a la impresora predeterminada. Revise la impresora del sistema.");
        }
    }

    private void buildShell() {
        root.getStyleClass().add("app");
        sidebar=new VBox(6);sidebar.getStyleClass().add("sidebar");sidebar.setPrefWidth(242);sidebar.setMinWidth(242);
        StackPane logo=new StackPane(new FontIcon("fth-layers"));logo.getStyleClass().add("marca-logo");
        brandTexts=new VBox(1,new Label("Gestion"),new Label("Panel administrativo"));
        brandTexts.getChildren().get(0).getStyleClass().add("marca-titulo");brandTexts.getChildren().get(1).getStyleClass().add("marca-subtitulo");
        HBox brand=new HBox(12,logo,brandTexts);brand.getStyleClass().add("marca");sidebar.getChildren().add(brand);
        indicator=new Region();indicator.getStyleClass().add("nav-indicador");indicator.setPrefHeight(42);indicator.setMaxHeight(42);
        VBox items=new VBox(3);
        addSection(items,"GENERAL");generateNavigation=addNavigation(items,"Generar documentos","fth-file-text",this::showGenerator);
        addSection(items,"DOCUMENTOS");addNavigation(items,"Variables","fth-tag",this::showDefinitions);addNavigation(items,"Plantillas","fth-folder",this::showLibrary);addNavigation(items,"Editor de plantillas","fth-edit-3",this::showEditor);
        addSection(items,"REGISTROS");addNavigation(items,"Clientes","fth-users",this::showContacts);addNavigation(items,"Historial","fth-clock",this::showHistory);
        addSection(items,"SISTEMA");addNavigation(items,"Conexiones API","fth-link",this::showApi);addNavigation(items,"Configuración y respaldos","fth-settings",this::showSettings);
        StackPane menuPane=new StackPane(indicator,items);menuPane.setAlignment(Pos.TOP_LEFT);sidebar.getChildren().add(menuPane);
        Region spacer=new Region();VBox.setVgrow(spacer,Priority.ALWAYS);sidebar.getChildren().add(spacer);
        Separator separator=new Separator();separator.getStyleClass().add("nav-separador");sidebar.getChildren().add(separator);
        themeButton=sidebarAction("Modo oscuro","fth-moon",this::changeTheme);
        collapseButton=sidebarAction("Contraer menú","fth-chevrons-left",this::collapseMenu);
        sidebar.getChildren().addAll(themeButton,collapseButton);
        menu.selectedToggleProperty().addListener((o,previous,current)->{
            if(current==null){menu.selectToggle(previous);return;}
            Runnable action=(Runnable)((ToggleButton)current).getUserData();
            action.run();moveIndicator(current);
        });
        workspace.getStyleClass().add("contenido");root.setLeft(sidebar);root.setCenter(workspace);
    }
    private void addSection(VBox items,String title){Label section=new Label(title);section.getStyleClass().add("nav-seccion");items.getChildren().add(section);menuTexts.add(section);}
    private ToggleButton addNavigation(VBox items,String title,String icon,Runnable action){ToggleButton button=new ToggleButton(title,new FontIcon(icon));button.setToggleGroup(menu);button.setMaxWidth(Double.MAX_VALUE);button.getStyleClass().add("nav-item");button.setUserData(action);Tooltip tip=new Tooltip(title);tip.setShowDelay(Duration.millis(300));button.setTooltip(tip);navigation.add(button);items.getChildren().add(button);return button;}
    private Button sidebarAction(String title,String icon,Runnable action){Button button=new Button(title,new FontIcon(icon));button.getStyleClass().add("nav-item");button.setMaxWidth(Double.MAX_VALUE);button.setOnAction(e->safe(action));return button;}
    private void moveIndicator(javafx.scene.control.Toggle toggle){if(toggle==null)return;Node button=(Node)toggle;double y=button.getBoundsInParent().getMinY();indicator.setPrefHeight(button.getBoundsInParent().getHeight());new Timeline(new KeyFrame(Duration.millis(280),new KeyValue(indicator.translateYProperty(),y,Interpolator.SPLINE(0.4,0,0.2,1)))).play();}
    private void changeTheme(){darkTheme=!darkTheme;Application.setUserAgentStylesheet(darkTheme?new PrimerDark().getUserAgentStylesheet():new PrimerLight().getUserAgentStylesheet());if(darkTheme)root.getStyleClass().add("dark");else root.getStyleClass().remove("dark");((FontIcon)themeButton.getGraphic()).setIconCode(darkTheme?Feather.SUN:Feather.MOON);themeButton.setText(darkTheme?"Modo claro":"Modo oscuro");Animations.fadeIn(workspace,Duration.millis(300)).play();}
    private void collapseMenu(){collapsed=!collapsed;double width=collapsed?76:242;Timeline transition=new Timeline(new KeyFrame(Duration.millis(260),new KeyValue(sidebar.prefWidthProperty(),width,Interpolator.SPLINE(0.4,0,0.2,1)),new KeyValue(sidebar.minWidthProperty(),width,Interpolator.SPLINE(0.4,0,0.2,1))));List<Node> texts=new ArrayList<>(menuTexts);texts.add(brandTexts);List<ButtonBase> buttons=new ArrayList<>(navigation);buttons.add(themeButton);buttons.add(collapseButton);if(collapsed){texts.forEach(n->{n.setVisible(false);n.setManaged(false);});buttons.forEach(b->b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY));transition.play();}else{transition.setOnFinished(e->{texts.forEach(n->{n.setVisible(true);n.setManaged(true);});buttons.forEach(b->b.setContentDisplay(ContentDisplay.LEFT));ParallelTransition fade=new ParallelTransition();texts.forEach(n->fade.getChildren().add(Animations.fadeIn(n,Duration.millis(200))));fade.play();Platform.runLater(()->moveIndicator(menu.getSelectedToggle()));});transition.play();}((FontIcon)collapseButton.getGraphic()).setIconCode(collapsed?Feather.CHEVRONS_RIGHT:Feather.CHEVRONS_LEFT);Platform.runLater(()->moveIndicator(menu.getSelectedToggle()));}
    private void page(String title,Node content){
        Label heading=new Label(title);heading.getStyleClass().add("pagina-titulo");
        if(!status.getStyleClass().contains("pagina-subtitulo"))status.getStyleClass().add("pagina-subtitulo");
        status.setText(switch(title){
            case "Generar documentos"->"Complete los datos y prepare su documento con una plantilla.";
            case "Variables"->"Defina campos reutilizables y vincule los datos que necesita.";
            case "Plantillas"->"Organice sus documentos en grupos y acceda al editor.";
            case "Editor de plantillas"->"Edite el documento e inserte sus variables.";
            case "Clientes"->"Consulte y actualice personas y empresas guardadas en este equipo.";
            case "Historial"->"Recupere documentos generados y reutilice sus datos.";
            case "Conexiones API"->"Administre credenciales y consulte DNI o RUC con guardado automático.";
            default->"Importe archivos y exporte sus plantillas o clientes.";
        });
        status.setWrapText(true);VBox headerText=new VBox(4,heading,status);HBox header=new HBox(headerText);header.getStyleClass().add("pagina-cabecera");
        VBox page=new VBox(header,content);page.getStyleClass().add("pagina");VBox.setVgrow(content,Priority.ALWAYS);workspace.getChildren().setAll(page);Animations.fadeInUp(page,Duration.millis(380)).play();
    }
    private Button button(String label,Runnable action){
        Button b=new Button(label.startsWith("+ ")?label.substring(2):label);b.getStyleClass().add("secondary-btn");
        if(label.startsWith("Guardar")||label.startsWith("Importar archivo"))b.getStyleClass().add("primary-btn");
        else if(label.startsWith("Eliminar"))b.getStyleClass().add("danger-btn");
        String icon=null;
        if(label.startsWith("Guardar"))icon="fth-save";
        else if(label.startsWith("Importar"))icon="fth-upload";
        else if(label.startsWith("Exportar")||label.startsWith("Descargar"))icon="fth-download";
        else if(label.startsWith("Eliminar"))icon="fth-trash-2";
        else if(label.startsWith("Duplicar"))icon="fth-copy";
        else if(label.startsWith("Consultar")||label.startsWith("Buscar"))icon="fth-search";
        else if(label.startsWith("Abrir"))icon="fth-external-link";
        else if(label.startsWith("Editar"))icon="fth-edit-2";
        else if(label.startsWith("Nueva")||label.startsWith("+ "))icon="fth-plus";
        if(icon!=null)b.setGraphic(new FontIcon(icon));
        b.setOnAction(e->safe(action));return b;
    }
    private void safe(Runnable action){try{action.run();}catch(Exception e){error(e);}}
    private void error(Exception e){status.setText("Error: "+e.getMessage());new Alert(Alert.AlertType.ERROR,"No se completó la operación:\n"+e.getMessage(),ButtonType.OK).showAndWait();}
    private void saved(String message){status.setText(message);}
    private static VBox labelled(String name,Node node){Label l=new Label(name);l.getStyleClass().add("field-label");VBox box=new VBox(5,l,node);box.setMinWidth(0);box.setMaxWidth(Double.MAX_VALUE);l.setWrapText(true);if(node instanceof Region r){r.setMinWidth(0);r.setMaxWidth(Double.MAX_VALUE);}return box;}

    private static GridPane twoColumns(){
        GridPane grid=new GridPane();grid.setHgap(14);grid.setVgap(14);grid.setMinWidth(0);
        for(int i=0;i<2;i++){ColumnConstraints col=new ColumnConstraints();col.setPercentWidth(50);col.setFillWidth(true);grid.getColumnConstraints().add(col);}return grid;
    }
    private static ScrollPane scrolling(Node content){ScrollPane scroll=new ScrollPane(content);scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);scroll.setMinWidth(0);scroll.getStyleClass().add("content-scroll");return scroll;}
    private static void uniformButtons(Pane pane){for(Node n:pane.getChildren())if(n instanceof Button b)b.setMaxWidth(Double.MAX_VALUE);}
    private static void fixedWidth(Region node,double width){node.setMinWidth(width);node.setPrefWidth(width);node.setMaxWidth(width);}
    private static Label description(String text){Label label=new Label(text);label.setWrapText(true);label.setMinWidth(0);label.getStyleClass().add("field-label");return label;}
    private static <T> void twoLineCells(ListView<T> list,java.util.function.Function<T,String> title,java.util.function.Function<T,String> detail){
        list.setCellFactory(view->new ListCell<>(){
            private final Label primary=new Label(),secondary=new Label();private final VBox box=new VBox(4,primary,secondary);
            {primary.setWrapText(true);secondary.setWrapText(true);secondary.getStyleClass().add("field-label");box.setMinWidth(0);box.prefWidthProperty().bind(widthProperty().subtract(28));box.setMaxWidth(Double.MAX_VALUE);}
            @Override protected void updateItem(T value,boolean empty){super.updateItem(value,empty);setText(null);setGraphic(empty||value==null?null:box);if(!empty&&value!=null){primary.setText(title.apply(value));secondary.setText(detail.apply(value));}}
        });
    }

    private void buildDefinitions(){
        definitionsPage=new BorderPane();definitionsPage.getStyleClass().add("page");
        definitionSearch.setPromptText("Buscar identificador, etiqueta o tipo");definitionSearch.textProperty().addListener((o,a,b)->refreshDefinitions());
        definitionList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->loadDefinition(b));
        twoLineCells(definitionList,Definition::label,d->d.key()+" · "+d.type());
        VBox left=new VBox(8,definitionSearch,definitionList,button("+ Nueva variable",this::newDefinition),button("Duplicar variable",this::duplicateDefinition),button("Eliminar variable",this::deleteDefinition));
        left.getStyleClass().add("side-panel");fixedWidth(left,340);uniformButtons(left);VBox.setVgrow(definitionList,Priority.ALWAYS);definitionsPage.setLeft(left);BorderPane.setMargin(left,new Insets(0,14,0,0));
        definitionType.getItems().setAll(TYPES);definitionApiService.getItems().setAll("DNI","RUC");definitionApiService.setValue("DNI");definitionApiService.setOnAction(e->refreshDefinitionApiFields());
        GridPane apiGrid=twoColumns();apiGrid.add(labelled("Servicio",definitionApiService),0,0);apiGrid.add(labelled("Dato solicitado",definitionApiField),1,0);
        definitionApiPath.setWrapText(true);definitionApiOptions=new VBox(12,apiGrid,definitionApiPath);definitionApiOptions.getStyleClass().add("binding-card");
        definitionApiEnabled.selectedProperty().addListener((o,a,b)->{definitionApiOptions.setVisible(b);definitionApiOptions.setManaged(b);});definitionApiOptions.setVisible(false);definitionApiOptions.setManaged(false);refreshDefinitionApiFields();
        GridPane fields=twoColumns();fields.add(labelled("Identificador estable",definitionKey),0,0);fields.add(labelled("Etiqueta visible",definitionLabel),1,0);fields.add(labelled("Tipo de entrada",definitionType),0,1);fields.add(labelled("Valor predeterminado opcional",definitionDefault),1,1);
        VBox form=new VBox(16,new Label("DEFINICIÓN DE VARIABLE"),fields,definitionApiEnabled,definitionApiOptions,button("Guardar variable",this::saveDefinition));form.getStyleClass().add("catalog-form");definitionsPage.setCenter(scrolling(form));newDefinition();
    }
    private void showDefinitions(){refreshDefinitions();page("Variables",definitionsPage);}
    private void refreshDefinitions(){try{long selected=selectedDefinition==null?0:selectedDefinition.id();String q=definitionSearch.getText().trim().toLowerCase();definitionList.getItems().setAll(store.definitions().stream().filter(d->(d.key()+" "+d.label()+" "+d.type()).toLowerCase().contains(q)).toList());if(selected!=0)definitionList.getSelectionModel().select(definitionList.getItems().stream().filter(d->d.id()==selected).findFirst().orElse(null));}catch(Exception e){error(e);}}
    private void loadDefinition(Definition d){if(d==null)return;selectedDefinition=d;definitionKey.setText(d.key());definitionKey.setDisable(true);definitionLabel.setText(d.label());definitionType.setValue(VariableInput.visualType(d.type()));definitionDefault.setText(d.defaultValue()==null?"":d.defaultValue());try{ApiBinding binding=data.apiBinding(d.id());definitionApiEnabled.setSelected(binding!=null);definitionApiService.setValue(binding==null?"DNI":binding.service());refreshDefinitionApiFields();if(binding!=null){String key=ApiFieldCatalog.canonical(binding.service(),binding.path());definitionApiField.setValue(key.startsWith("legacy:")?"Campo anterior sin equivalencia":ApiFieldCatalog.label(binding.service(),key));}}catch(Exception e){error(e);}}
    private void newDefinition(){selectedDefinition=null;definitionList.getSelectionModel().clearSelection();definitionKey.setDisable(false);definitionKey.clear();definitionLabel.clear();definitionType.setValue(TYPES.get(0));definitionDefault.clear();definitionApiEnabled.setSelected(false);definitionApiService.setValue("DNI");definitionApiField.setValue(null);}
    private void refreshDefinitionApiFields(){String current=definitionApiField.getValue();definitionApiField.getItems().setAll(ApiFieldCatalog.fields(definitionApiService.getValue()).stream().map(ApiFieldCatalog.Field::label).toList());if(current!=null&&definitionApiField.getItems().contains(current))definitionApiField.setValue(current);else definitionApiField.getSelectionModel().selectFirst();}
    private void saveDefinition(){String key=definitionKey.getText().trim(),label=definitionLabel.getText().trim();if(!key.matches("[a-zA-Z][a-zA-Z0-9_]*"))throw new IllegalArgumentException("El identificador debe empezar con letra y contener letras, números o guion bajo.");if(label.isBlank())throw new IllegalArgumentException("Escriba una etiqueta.");try{String type=definitionType.getValue();if(!definitionDefault.getText().isBlank()&&!"Fecha".equals(type))VariableInput.validate(type,definitionDefault.getText(),Locale.getDefault());String stored=null;if(definitionApiEnabled.isSelected()){String requested=definitionApiField.getValue();ApiFieldCatalog.Field field=ApiFieldCatalog.fields(definitionApiService.getValue()).stream().filter(f->f.label().equals(requested)).findFirst().orElse(null);if(field!=null)stored="field:"+field.key();else if(selectedDefinition!=null&&"Campo anterior sin equivalencia".equals(requested)){ApiBinding prior=data.apiBinding(selectedDefinition.id());if(prior!=null)stored=prior.path();}if(stored==null)throw new IllegalArgumentException("Seleccione un dato solicitado.");}Definition saved=store.saveDefinition(new Definition(selectedDefinition==null?0:selectedDefinition.id(),key,label,type,definitionDefault.getText()));if(definitionApiEnabled.isSelected())data.saveApiBinding(new ApiBinding(saved.id(),definitionApiService.getValue(),stored));else data.deleteApiBinding(saved.id());selectedDefinition=saved;refreshDefinitions();refreshVariables();saved("Variable guardada en el catálogo");}catch(Exception e){error(e);}}
    private void duplicateDefinition(){Definition selected=definitionList.getSelectionModel().getSelectedItem();if(selected==null)return;try{Set<String> keys=new HashSet<>();for(Definition d:store.definitions())keys.add(d.key());String base=selected.key()+"_copia",key=base;int suffix=2;while(keys.contains(key))key=base+suffix++;Definition copy=store.saveDefinition(new Definition(0,key,selected.label()+" (copia)",VariableInput.visualType(selected.type()),""));selectedDefinition=copy;refreshDefinitions();definitionList.getSelectionModel().select(copy);saved("Variable duplicada sin datos ni asociaciones");}catch(Exception e){error(e);}}
    private void deleteDefinition(){Definition selected=definitionList.getSelectionModel().getSelectedItem();if(selected==null)return;try{List<String> uses=store.definitionDependencies(selected.id());if(!uses.isEmpty()){ButtonType open=new ButtonType("Abrir editor");Alert warning=new Alert(Alert.AlertType.WARNING,"La variable se utiliza en estas plantillas:\n"+String.join("\n",uses)+"\n\nQuite o reemplace cada aparición y desvincule la variable antes de eliminarla.",open,ButtonType.CANCEL);if(warning.showAndWait().orElse(ButtonType.CANCEL)==open)showEditor();return;}Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la variable "+selected.key()+"?",ButtonType.YES,ButtonType.CANCEL);if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;data.deleteApiBinding(selected.id());store.deleteDefinition(selected.id());newDefinition();refreshDefinitions();refreshVariables();saved("Variable eliminada");}catch(Exception e){error(e);}}
    private void editDefinitionFromEditor(){Definition d=variableList.getSelectionModel().getSelectedItem();menu.getToggles().stream().filter(t->t instanceof ToggleButton b&&"Variables".equals(b.getText())).findFirst().ifPresent(menu::selectToggle);if(d!=null){selectedDefinition=d;definitionList.getSelectionModel().select(definitionList.getItems().stream().filter(x->x.id()==d.id()).findFirst().orElse(null));}}

    private BorderPane editorPage;
    private void buildEditor(){
        editorPage=new BorderPane();editorPage.getStyleClass().add("page");
        BorderPane documentPage=new BorderPane();documentPage.getStyleClass().add("page");
        FlowPane toolbar=new FlowPane(8,6);toolbar.setAlignment(Pos.CENTER_LEFT);
        templateChoice.setPrefWidth(240);templateChoice.setPromptText("Seleccione plantilla");
        templateChoice.setOnAction(e->{if(!updatingTemplates)safe(()->openTemplate(templateChoice.getValue()));});
        templateName.setPromptText("Nombre de la plantilla");templateName.setPrefWidth(190);templateName.textProperty().addListener((o,a,b)->{if(currentTemplate!=null&&!Objects.equals(b,currentTemplate.name()))dirty=true;});
        toolbar.getChildren().addAll(button("Abrir Plantillas",this::showLibrary),button("Importar DOCX",this::importDocx),button("Nueva",this::newTemplate),templateName,button("Guardar plantilla",this::requestSaveTemplate),button("Asociaciones",this::showTemplateAssociations));
        toolbar.getStyleClass().add("toolbar");
        toolbar.getStyleClass().add("editor-toolbar");VBox top=new VBox(4,toolbar,buildEditorTools());documentPage.setTop(top);
        paletteSearch.setPromptText("Buscar variable");paletteSearch.textProperty().addListener((o,a,b)->refreshVariables());
        palettePanel=new VBox(7,new Label("VARIABLES"),paletteSearch,variableList,button("Insertar en cursor",this::insertSelected),button("Abrir Variables",this::editDefinitionFromEditor));
        palettePanel.getStyleClass().add("side-panel");palettePanel.setPrefWidth(190);palettePanel.setMinWidth(180);VBox.setVgrow(variableList,Priority.ALWAYS);
        variableList.setCellFactory(list->new ListCell<>(){
            @Override protected void updateItem(Definition v,boolean empty){super.updateItem(v,empty);setText(empty||v==null?null:v.label());setTooltip(empty||v==null?null:new Tooltip(v.label()+"\n"+v.key()+" · "+v.type()+(v.defaultValue()==null||v.defaultValue().isBlank()?"":"\nPredeterminado: "+v.defaultValue())));}
        });
        variableList.setOnDragDetected(e->{Definition v=variableList.getSelectionModel().getSelectedItem();if(v!=null){Dragboard db=variableList.startDragAndDrop(TransferMode.COPY);ClipboardContent cc=new ClipboardContent();cc.putString("definition:"+v.id());db.setContent(cc);e.consume();}});
        documentPage.setLeft(palettePanel);
        richEditor.setContextMenuEnabled(true);
        richEditor.getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{if(b==Worker.State.SUCCEEDED){if(document==null||richModel==null||!Boolean.TRUE.equals(richEditor.getEngine().executeScript("typeof window.studio !== 'undefined'"))){editorReady=false;return;}editorReady=true;((JSObject)richEditor.getEngine().executeScript("window")).setMember("javaBridge",editorBridge);richEditor.getEngine().executeScript("window.studio.paper("+pageWidth+","+pageHeight+","+editorLeft+","+editorTop+","+editorRight+","+editorBottom+")");updateZoom();}});
        richEditor.getEngine().locationProperty().addListener((o,a,b)->{if(b!=null&&(b.startsWith("http:")||b.startsWith("https:")))richEditor.getEngine().loadContent("");});
        richEditor.widthProperty().addListener((o,a,b)->updateZoom());richEditor.heightProperty().addListener((o,a,b)->updateZoom());
        richEditor.setOnDragOver(e->{String ref=e.getDragboard().getString();if(ref!=null&&ref.startsWith("definition:")&&editorReady){e.acceptTransferModes(TransferMode.COPY);richEditor.getEngine().executeScript("window.studio.showDrop("+e.getX()+","+e.getY()+")");e.consume();}});
        richEditor.setOnDragExited(e->{if(editorReady)richEditor.getEngine().executeScript("window.studio.hideDrop()");});
        richEditor.setOnDragDropped(e->{String ref=e.getDragboard().getString();if(ref==null||!ref.startsWith("definition:"))return;boolean done=false;if(editorReady){try{long id=Long.parseLong(ref.substring(11));Definition d=store.definitions().stream().filter(v->v.id()==id).findFirst().orElse(null);if(d!=null&&bindVariable(d))done=Boolean.TRUE.equals(richEditor.getEngine().executeScript("window.studio.insertAt("+e.getX()+","+e.getY()+","+jsonString(d.key())+")"));}catch(Exception ex){error(ex);}}e.setDropCompleted(done);if(editorReady)richEditor.getEngine().executeScript("window.studio.hideDrop()");e.consume();});
        documentPage.setCenter(richEditor);
        editorTabs=new TabPane(new Tab("Documento",documentPage));editorTabs.getTabs().get(0).setClosable(false);editorPage.setCenter(editorTabs);
    }
    private void attachGenerationConfiguration(){Tab config=new Tab("Configurar generación",catalogPage);config.setClosable(false);editorTabs.getTabs().add(config);editorTabs.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b==config){if(dirty)safe(this::saveTemplate);refreshTemplates();refreshKinds();if(currentTemplate!=null&&kindTemplate.getValue()==null){kindTemplate.setValue(currentTemplate);}}});}
    private void showEditor(){refreshTemplates();refreshVariables();editorTabs.getSelectionModel().select(0);page("Editor de plantillas",editorPage);if(document==null)richEditor.getEngine().loadContent("<html><body style='font:14px Arial;padding:30px'>Importe un DOCX o cree una plantilla nueva.</body></html>");}
    private void showTemplateAssociations(){
        if(currentTemplate==null)throw new IllegalArgumentException("Abra primero una plantilla.");
        try{
            if(dirty)saveTemplate();
            long templateId=currentTemplate.id();List<Variable> variables=variablesForTemplate(currentTemplate);
            Dialog<ButtonType> dialog=new Dialog<>();dialog.setTitle("Asociaciones de la plantilla");dialog.initOwner(stage);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            ComboBox<Variable> apiVariable=new ComboBox<>(),contactVariable=new ComboBox<>();apiVariable.getItems().setAll(variables);contactVariable.getItems().setAll(variables);
            ComboBox<String> apiService=new ComboBox<>(),contactKind=new ComboBox<>(),contactField=new ComboBox<>();apiService.getItems().setAll("DNI","RUC");apiService.setValue("DNI");contactKind.getItems().setAll("Persona","Empresa");contactKind.setValue("Persona");
            Runnable refreshFields=()->{contactField.getItems().setAll("Empresa".equals(contactKind.getValue())?List.of("Razón social","RUC","Domicilio fiscal","Representantes"):List.of("Nombres","Apellidos","Tipo de documento","Número de documento","Domicilio"));contactField.getSelectionModel().selectFirst();};contactKind.setOnAction(e->refreshFields.run());refreshFields.run();
            TextField apiGroup=new TextField(),contactGroup=new TextField();apiGroup.setPromptText("Grupo independiente");contactGroup.setPromptText("Grupo independiente");CheckBox source=new CheckBox("Número a consultar");
            ListView<ApiContext> apiList=new ListView<>();ListView<ContactBinding> contactList=new ListView<>();apiList.setPrefHeight(110);contactList.setPrefHeight(110);
            Runnable refresh=()->{try{apiList.getItems().setAll(data.apiContexts(templateId));contactList.getItems().setAll(data.contactBindings(templateId));}catch(Exception ex){error(ex);}};refresh.run();
            apiList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null){apiGroup.setText(b.groupKey());apiService.setValue(b.service());source.setSelected(b.source());apiVariable.getSelectionModel().select(variables.stream().filter(v->v.key().equals(b.variableKey())).findFirst().orElse(null));}});
            Button addApi=button("Guardar consulta",()->{try{Variable v=apiVariable.getValue();if(v==null||apiGroup.getText().isBlank())throw new IllegalArgumentException("Elija variable y grupo.");data.saveApiContext(new ApiContext(templateId,v.key(),apiGroup.getText().trim(),apiService.getValue(),source.isSelected()));refresh.run();saved("Consulta vinculada a la plantilla");}catch(Exception ex){error(ex);}});
            Button removeApi=button("Quitar consulta",()->{try{ApiContext c=apiList.getSelectionModel().getSelectedItem();if(c!=null){data.deleteApiContext(templateId,c.variableKey());refresh.run();}}catch(Exception ex){error(ex);}});
            Button addContact=button("Guardar cliente",()->{try{Variable v=contactVariable.getValue();if(v==null||contactGroup.getText().isBlank())throw new IllegalArgumentException("Elija variable y grupo.");String field=switch(contactField.getValue()){case "Nombres","Razón social"->"given";case "Apellidos"->"family";case "Tipo de documento"->"document_type";case "Número de documento","RUC"->"number";case "Domicilio","Domicilio fiscal"->"address";case "Representantes"->"representatives";default->throw new IllegalArgumentException("Elija un dato.");};data.saveContactBinding(new ContactBinding(0,templateId,contactGroup.getText().trim(),contactKind.getValue(),field,v.key()));refresh.run();saved("Cliente vinculado a la plantilla");}catch(Exception ex){error(ex);}});
            Button removeContact=button("Quitar cliente",()->{try{ContactBinding b=contactList.getSelectionModel().getSelectedItem();if(b!=null){data.deleteContactBinding(b.id());refresh.run();}}catch(Exception ex){error(ex);}});
            VBox body=new VBox(9,new Label("CONSULTAS DNI / RUC"),new Label("Use un grupo distinto por cada persona o empresa."),new HBox(8,apiGroup,apiService,apiVariable,source),new HBox(8,addApi,removeApi),apiList,new Separator(),new Label("CLIENTES LOCALES"),new HBox(8,contactGroup,contactKind,contactField,contactVariable),new HBox(8,addContact,removeContact),contactList);body.getStyleClass().add("catalog-form");dialog.getDialogPane().setContent(new ScrollPane(body));dialog.getDialogPane().setPrefWidth(830);dialog.showAndWait();
        }catch(Exception e){error(e);}
    }
    private void refreshTemplates(){try{updatingTemplates=true;Template selected=currentTemplate;templateChoice.getItems().setAll(store.templates());kindTemplate.getItems().setAll(templateChoice.getItems());if(selected!=null)templateChoice.getSelectionModel().select(templateChoice.getItems().stream().filter(t->t.id()==selected.id()).findFirst().orElse(null));}catch(Exception e){error(e);}finally{updatingTemplates=false;}}
    private void newTemplate(){TextInputDialog name=new TextInputDialog();name.setTitle("Nueva plantilla");name.setHeaderText("Nombre de la plantilla");String selected=name.showAndWait().orElse(null);if(selected==null)return;if(selected.isBlank())throw new IllegalArgumentException("Escriba un nombre.");if(!confirmPendingSwitch())return;try{XWPFDocument d=new XWPFDocument();d.createParagraph().createRun().setText("Escriba aquí el contenido del documento.");byte[] content=DocumentEngine.bytes(d);d.close();Template t=store.saveTemplate(new Template(0,selected.trim(),content,content));data.recordRevision(t.id(),content);TemplateGroup selectedGroup=groupList.getSelectionModel().getSelectedItem();if(selectedGroup!=null)store.setTemplateGroups(t.id(),List.of(selectedGroup.id()));refreshTemplates();refreshLibrary();refreshKinds();openTemplate(t);saved("Plantilla creada");}catch(Exception e){error(e);}}
    private void importDocx(){FileChooser fc=new FileChooser();fc.setTitle("Importar documento Word");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word DOCX","*.docx"));File file=fc.showOpenDialog(stage);if(file==null||!confirmPendingSwitch())return;try{byte[] content=Files.readAllBytes(file.toPath());try(XWPFDocument check=DocumentEngine.open(content)){}Template t=store.saveTemplate(new Template(0,file.getName().replaceFirst("(?i)\\.docx$",""),content,content));data.recordRevision(t.id(),content);TemplateGroup selectedGroup=groupList.getSelectionModel().getSelectedItem();if(selectedGroup!=null)store.setTemplateGroups(t.id(),List.of(selectedGroup.id()));refreshTemplates();refreshLibrary();refreshKinds();openTemplate(t);saved("DOCX importado y guardado localmente");}catch(Exception e){error(e);}}
    private boolean confirmPendingSwitch(){if(document==null||!dirty)return true;Alert choice=new Alert(Alert.AlertType.CONFIRMATION,"Hay cambios pendientes. ¿Guardar antes de abrir otra plantilla?",ButtonType.YES,ButtonType.NO,ButtonType.CANCEL);ButtonType result=choice.showAndWait().orElse(ButtonType.CANCEL);if(result==ButtonType.CANCEL)return false;if(result==ButtonType.YES)saveTemplate();else dirty=false;return true;}
    private void openTemplate(Template t){if(t==null)return;try{if(currentTemplate!=null&&currentTemplate.id()!=t.id()&&!confirmPendingSwitch())return;if(document!=null)document.close();currentTemplate=t;document=DocumentEngine.open(t.edited());selectedRun=null;selectedParagraph=null;selectedText=null;templateName.setText(t.name());dirty=false;refreshVariables();renderDocument();updatingTemplates=true;templateChoice.getSelectionModel().select(templateChoice.getItems().stream().filter(x->x.id()==t.id()).findFirst().orElse(t));updatingTemplates=false;}catch(Exception e){updatingTemplates=false;error(e);}}
    private void requestSaveTemplate(){if(document==null)return;if(!dirty&&Objects.equals(templateName.getText().trim(),currentTemplate.name())){saved("La plantilla ya está guardada");return;}if(new Alert(Alert.AlertType.CONFIRMATION,"¿Guardar los cambios de esta plantilla?",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)==ButtonType.YES)saveTemplate();}
    private void saveTemplate(){if(document==null)return;if(templateName.getText().isBlank())throw new IllegalArgumentException("Escriba un nombre de plantilla.");try{syncEditor();byte[] content=DocumentEngine.bytes(document);boolean contentChanged=!Arrays.equals(content,currentTemplate.edited());boolean nameChanged=!templateName.getText().trim().equals(currentTemplate.name());if(!contentChanged&&!nameChanged){dirty=false;saved("La plantilla ya está guardada");return;}currentTemplate=store.saveTemplate(new Template(currentTemplate.id(),templateName.getText().trim(),currentTemplate.original(),content));if(contentChanged)data.recordRevision(currentTemplate.id(),content);dirty=false;refreshTemplates();refreshLibrary();refreshKinds();saved("Plantilla guardada");renderDocument();}catch(Exception e){throw new RuntimeException("No se pudo guardar la plantilla",e);}}
    private void refreshVariables(){try{long selected=variableList.getSelectionModel().getSelectedItem()==null?0:variableList.getSelectionModel().getSelectedItem().id();String q=paletteSearch.getText().trim().toLowerCase();variableList.getItems().setAll(store.definitions().stream().filter(d->(d.label()+" "+d.key()).toLowerCase().contains(q)).toList());if(selected!=0)variableList.getSelectionModel().select(variableList.getItems().stream().filter(d->d.id()==selected).findFirst().orElse(null));}catch(Exception e){error(e);}}
    private void buildLibrary(){
        libraryPage=new BorderPane();libraryPage.getStyleClass().add("page");librarySearch.setPromptText("Buscar plantilla");librarySearch.getStyleClass().add("buscador");librarySearch.textProperty().addListener((o,a,b)->refreshLibrary());
        groupList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(!refreshingLibrary)refreshLibrary();});
        VBox groups=new VBox(8,new Label("GRUPOS"),groupList,button("+ Nuevo grupo",this::createGroup),button("Renombrar grupo",this::renameGroup),button("Eliminar grupo",this::removeGroup));groups.getStyleClass().add("side-panel");fixedWidth(groups,240);uniformButtons(groups);BorderPane.setMargin(groups,new Insets(0,12,0,0));VBox.setVgrow(groupList,Priority.ALWAYS);libraryPage.setLeft(groups);
        librarySort.getItems().setAll("Nombre A-Z","Nombre Z-A","Más recientes","Más antiguas");librarySort.setValue("Nombre A-Z");librarySort.setOnAction(e->refreshLibrary());
        HBox searchRow=new HBox(8,librarySearch,librarySort);HBox.setHgrow(librarySearch,Priority.ALWAYS);librarySearch.setMaxWidth(Double.MAX_VALUE);librarySort.setPrefWidth(135);
        groupList.setPlaceholder(description("Cree su primer grupo para organizar las plantillas."));
        VBox templates=new VBox(9,searchRow,libraryList);templates.setMinWidth(0);templates.getStyleClass().add("catalog-form");templates.setMaxWidth(Double.MAX_VALUE);VBox.setVgrow(libraryList,Priority.ALWAYS);libraryPage.setCenter(templates);
        libraryList.setCellFactory(view->new ListCell<>(){
            private final Label title=new Label(),detail=new Label();private final VBox lines=new VBox(4,title,detail);
            {title.setWrapText(true);detail.setWrapText(true);detail.getStyleClass().add("field-label");lines.setMinWidth(0);lines.prefWidthProperty().bind(widthProperty().subtract(28));}
            @Override protected void updateItem(Template template,boolean empty){
                super.updateItem(template,empty);getStyleClass().remove("library-associated");setText(null);setGraphic(null);
                if(empty||template==null)return;
                List<Long> memberships=libraryMemberships.getOrDefault(template.id(),List.of());TemplateGroup group=groupList.getSelectionModel().getSelectedItem();
                if(group!=null&&libraryHighlighted(memberships,group.id()))getStyleClass().add("library-associated");
                title.setText(template.name());String names=memberships.stream().map(libraryGroupNames::get).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining(", "));
                String date=libraryModifiedDates.get(template.id());detail.setText(names+(date==null?"":" · "+date));setGraphic(lines);
            }
        });
        libraryGroups.setWrapText(true);addToGroup=button("Añadir plantilla al grupo",this::addLibraryGroup);removeFromGroup=button("Quitar del grupo",this::removeLibraryGroup);
        VBox actions=new VBox(9,new Label("PLANTILLA SELECCIONADA"),button("Abrir en editor",this::openLibraryTemplate),button("Renombrar plantilla",this::renameLibraryTemplate),button("Duplicar plantilla",this::duplicateLibraryTemplate),button("Eliminar plantilla",this::removeLibraryTemplate),new Separator(),new Label("GRUPOS ASIGNADOS"),libraryGroups,addToGroup,removeFromGroup);actions.getStyleClass().add("side-panel");fixedWidth(actions,270);uniformButtons(actions);libraryPage.setRight(scrolling(actions));BorderPane.setMargin(libraryPage.getRight(),new Insets(0,0,0,12));
        libraryList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->updateLibraryActions());updateLibraryActions();
    }
    private void showLibrary(){refreshLibrary();page("Plantillas",libraryPage);}
    private void refreshLibrary(){if(refreshingLibrary)return;refreshingLibrary=true;try{
        Template selected=libraryList.getSelectionModel().getSelectedItem();TemplateGroup selectedGroup=groupList.getSelectionModel().getSelectedItem();String q=librarySearch.getText().trim().toLowerCase();List<Template> all=store.templates();List<TemplateGroup> groups=store.groups();Map<Long,String> dates=data.revisionDates();Map<Long,List<Long>> memberships=new HashMap<>();for(Template t:all)memberships.put(t.id(),store.groupIds(t.id()));libraryMemberships=memberships;libraryModifiedDates=dates;Map<Long,String> names=new HashMap<>();for(TemplateGroup g:groups)names.put(g.id(),g.name());libraryGroupNames=names;
        groupList.getItems().setAll(groups);
        TemplateGroup active=groups.stream().filter(g->selectedGroup!=null&&g.id()==selectedGroup.id()).findFirst().orElse(groups.isEmpty()?null:groups.get(0));
        groupList.getSelectionModel().select(active);
        libraryList.setPlaceholder(description(all.isEmpty()?"Todavía no hay plantillas guardadas. Créelas o impórtelas desde el Editor de plantillas.":"No hay plantillas que coincidan con la búsqueda."));
        List<Template> found=new ArrayList<>();for(Template t:all)if(libraryMatches(t.name(),q))found.add(t);
        Comparator<Template> name=Comparator.comparing(Template::name,String.CASE_INSENSITIVE_ORDER).thenComparingLong(Template::id);String sort=librarySort.getValue();Comparator<Template> order=switch(sort==null?"Nombre A-Z":sort){case "Nombre Z-A"->name.reversed();case "Más recientes"->Comparator.comparing((Template t)->dates.get(t.id()),Comparator.nullsFirst(String::compareTo)).reversed().thenComparing(name);case "Más antiguas"->Comparator.comparing((Template t)->dates.get(t.id()),Comparator.nullsLast(String::compareTo)).thenComparing(name);default->name;};found.sort(order);
        libraryList.getItems().setAll(found);if(selected!=null)libraryList.getSelectionModel().select(found.stream().filter(t->t.id()==selected.id()).findFirst().orElse(null));libraryList.refresh();updateLibraryActions();
    }catch(Exception ex){error(ex);}finally{refreshingLibrary=false;}}
    static boolean libraryMatches(String name,String search){return name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));}
    static boolean libraryHighlighted(List<Long> memberships,long selectedGroup){return selectedGroup>0&&memberships.contains(selectedGroup);}
    private void updateLibraryActions(){try{Template t=libraryList.getSelectionModel().getSelectedItem();TemplateGroup group=groupList.getSelectionModel().getSelectedItem();List<Long> ids=t==null?List.of():store.groupIds(t.id());String names=store.groups().stream().filter(g->ids.contains(g.id())).map(TemplateGroup::name).collect(java.util.stream.Collectors.joining(", "));libraryGroups.setText(t==null?"Seleccione una plantilla":names.isBlank()?"Sin grupo":names);boolean ready=t!=null&&group!=null&&group.id()>0;boolean member=ready&&ids.contains(group.id());if(addToGroup!=null)addToGroup.setDisable(group==null);if(removeFromGroup!=null)removeFromGroup.setDisable(!ready||!member);}catch(Exception ex){error(ex);}}
    private void addLibraryGroup(){try{
        TemplateGroup group=selectedGroup();List<Template> available=store.templates().stream().filter(t->!libraryMemberships.getOrDefault(t.id(),List.of()).contains(group.id())).toList();
        if(available.isEmpty()){saved("Todas las plantillas guardadas ya pertenecen a este grupo. Cree o importe otra plantilla.");return;}
        ChoiceDialog<Template> choice=new ChoiceDialog<>(available.get(0),available);choice.setTitle("Añadir plantilla al grupo");choice.setHeaderText("Seleccione una plantilla para "+group.name());
        choice.showAndWait().ifPresent(t->{try{Set<Long> ids=new LinkedHashSet<>(store.groupIds(t.id()));ids.add(group.id());store.setTemplateGroups(t.id(),ids);refreshLibrary();libraryList.getSelectionModel().select(t);saved("Plantilla añadida al grupo");}catch(Exception ex){error(ex);}});
    }catch(Exception ex){error(ex);}}
    private void removeLibraryGroup(){changeLibraryGroup(false);}
    private void changeLibraryGroup(boolean add){try{Template t=selectedLibraryTemplate();TemplateGroup g=selectedGroup();Set<Long> ids=new LinkedHashSet<>(store.groupIds(t.id()));if(add)ids.add(g.id());else ids.remove(g.id());store.setTemplateGroups(t.id(),ids);refreshLibrary();saved(add?"Plantilla añadida al grupo":"Plantilla quitada del grupo");}catch(Exception ex){error(ex);}}
    private Template selectedLibraryTemplate(){Template t=libraryList.getSelectionModel().getSelectedItem();if(t==null)throw new IllegalArgumentException("Seleccione una plantilla.");return t;}
    private TemplateGroup selectedGroup(){TemplateGroup g=groupList.getSelectionModel().getSelectedItem();if(g==null||g.id()<=0)throw new IllegalArgumentException("Seleccione un grupo de Mis grupos.");return g;}
    private void createGroup(){TextInputDialog input=new TextInputDialog();input.setTitle("Nuevo grupo");input.setHeaderText("Nombre del grupo de plantillas");input.showAndWait().ifPresent(name->{try{TemplateGroup g=store.saveGroup(0,name);refreshLibrary();groupList.getSelectionModel().select(g);saved("Grupo creado");}catch(Exception ex){error(ex);}});}
    private void renameGroup(){TemplateGroup g=selectedGroup();TextInputDialog input=new TextInputDialog(g.name());input.setTitle("Renombrar grupo");input.setHeaderText("Nombre del grupo");input.showAndWait().ifPresent(name->{try{store.saveGroup(g.id(),name);refreshLibrary();saved("Grupo renombrado");}catch(Exception ex){error(ex);}});}
    private void removeGroup(){try{TemplateGroup g=selectedGroup();if(new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar el grupo '"+g.name()+"'? Sus "+g.count()+" plantillas permanecerán guardadas.",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;store.deleteGroup(g.id(),null);groupList.getSelectionModel().clearSelection();refreshLibrary();saved("Grupo eliminado; las plantillas permanecen guardadas");}catch(Exception ex){error(ex);}}
    private void openLibraryTemplate(){Template t=selectedLibraryTemplate();showEditor();openTemplate(t);}
    private void renameLibraryTemplate(){Template t=selectedLibraryTemplate();TextInputDialog input=new TextInputDialog(t.name());input.setTitle("Renombrar plantilla");input.setHeaderText("Nombre de la plantilla");input.showAndWait().ifPresent(name->{try{if(name.isBlank())throw new IllegalArgumentException("Escriba un nombre.");store.saveTemplate(new Template(t.id(),name.trim(),t.original(),t.edited()));if(currentTemplate!=null&&currentTemplate.id()==t.id()){currentTemplate=new Template(t.id(),name.trim(),t.original(),t.edited());templateName.setText(name.trim());}refreshLibrary();refreshTemplates();saved("Plantilla renombrada");}catch(Exception ex){error(ex);}});}
    private void duplicateLibraryTemplate(){Template t=selectedLibraryTemplate();try{Template copy=store.saveTemplate(new Template(0,t.name()+" (copia)",t.original(),t.edited()));for(Variable v:store.variables(t.id())){Variable created=store.saveVariable(new Variable(0,copy.id(),v.key(),v.label(),v.type(),v.defaultValue(),v.required(),v.position()));long def=store.definitionId(v.id());if(def>0)store.linkDefinition(created.id(),def);}for(ApiContext c:data.apiContexts(t.id()))data.saveApiContext(new ApiContext(copy.id(),c.variableKey(),c.groupKey(),c.service(),c.source()));for(ContactBinding b:data.contactBindings(t.id()))data.saveContactBinding(new ContactBinding(0,copy.id(),b.groupKey(),b.kind(),b.field(),b.variableKey()));data.recordRevision(copy.id(),copy.edited());refreshLibrary();refreshTemplates();libraryList.getSelectionModel().select(copy);saved("Plantilla duplicada sin grupos");}catch(Exception ex){error(ex);}}
    private void removeLibraryTemplate(){Template t=selectedLibraryTemplate();try{if(data.history().stream().anyMatch(h->h.templateId()==t.id()))throw new IllegalStateException("La plantilla tiene documentos en el historial y debe conservarse.");List<String> legacy=store.templateDependencies(t.id());String detail=legacy.isEmpty()?"":" También se quitarán "+legacy.size()+" configuraciones antiguas vinculadas a esta plantilla.";if(new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la plantilla '"+t.name()+"'?"+detail,ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;store.deleteTemplateWithLegacyKinds(t.id());data.clearMappings(t.id());data.clearContactBindings(t.id());data.clearApiContexts(t.id());if(currentTemplate!=null&&currentTemplate.id()==t.id()){if(document!=null)document.close();document=null;currentTemplate=null;richModel=null;editorReady=false;}refreshLibrary();refreshTemplates();refreshKinds();saved("Plantilla eliminada");}catch(Exception ex){error(ex);}}
    private void insertSelected(){Definition d=variableList.getSelectionModel().getSelectedItem();if(d==null||document==null||!editorReady){saved("Seleccione una variable y coloque el cursor en el documento");return;}if(bindVariable(d))richEditor.getEngine().executeScript("window.studio.insertToken("+jsonString(d.key())+")");}
    private boolean bindVariable(Definition d){try{if(currentTemplate==null)return false;Variable bound=store.variables(currentTemplate.id()).stream().filter(v->v.key().equals(d.key())).findFirst().orElse(null);if(bound==null)store.bindDefinition(currentTemplate.id(),d.id(),false,store.variables(currentTemplate.id()).size()+1);else if(store.definitionId(bound.id())!=d.id())throw new IllegalArgumentException("Esta plantilla ya utiliza '"+d.key()+"' con otra definición.");return true;}catch(Exception ex){error(ex);return false;}}
    private String jsonString(String value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new RuntimeException(ex);}}
    private void editorCommand(String name){editorCommand(name,null);}
    private void editorCommand(String name,String value){if(editorReady)richEditor.getEngine().executeScript("window.studio.command("+jsonString(name)+","+(value==null?"null":jsonString(value))+")");}
    private void syncEditor() throws Exception {if(editorReady&&richModel!=null&&document!=null){String xml=String.valueOf(richEditor.getEngine().executeScript("window.studio.html()"));richModel.apply(xml);XWPFDocument normalized=DocumentEngine.open(DocumentEngine.bytes(document));document.close();document=normalized;}}
    public final class EditorBridge {public void markDirty(){Platform.runLater(()->{dirty=true;status.setText("Cambios sin guardar");});}public void pageMoved(){Platform.runLater(StudioWindow.this::updatePageIndicator);}public void tableSelected(boolean selected){Platform.runLater(()->{if(tableTools!=null){tableTools.setVisible(selected);tableTools.setManaged(selected);}});}public void imageSelected(boolean selected){Platform.runLater(()->{if(imageTools!=null){imageTools.setVisible(selected);imageTools.setManaged(selected);}});}}
    private void tableAction(String action){tableAction(action,null);}
    private void tableAction(String action,String value){if(editorReady)richEditor.getEngine().executeScript("window.studio.tableAction("+jsonString(action)+","+(value==null?"null":jsonString(value))+")");}
    private void imageAction(String action){if(editorReady)richEditor.getEngine().executeScript("window.studio.imageAction("+jsonString(action)+")");}
    private MenuButton tableGrid(){MenuButton picker=new MenuButton("",new FontIcon("fth-grid"));picker.getStyleClass().add("button-outlined");picker.setTooltip(new Tooltip("Insertar tabla"));GridPane grid=new GridPane();grid.setHgap(2);grid.setVgap(2);Label dimensions=new Label("Seleccione filas y columnas");VBox content=new VBox(5,dimensions,grid);for(int row=1;row<=8;row++)for(int col=1;col<=8;col++){final int rows=row,cols=col;Button square=new Button();square.getStyleClass().add("button-outlined");square.setMinSize(23,23);square.setPrefSize(23,23);square.setMaxSize(23,23);square.setOnMouseEntered(e->{dimensions.setText(cols+" columnas × "+rows+" filas");for(Node n:grid.getChildren()){int c=GridPane.getColumnIndex(n)==null?0:GridPane.getColumnIndex(n),r=GridPane.getRowIndex(n)==null?0:GridPane.getRowIndex(n);if(c<cols&&r<rows){if(!n.getStyleClass().contains("accent"))n.getStyleClass().add("accent");}else n.getStyleClass().remove("accent");}});square.setOnAction(e->{picker.hide();if(editorReady)richEditor.getEngine().executeScript("window.studio.insertTable("+cols+","+rows+")");});grid.add(square,col-1,row-1);}picker.getItems().add(new CustomMenuItem(content,false));return picker;}
    private Node buildEditorTools(){
        fontChoice.setEditable(false);fontChoice.setPrefWidth(145);fontChoice.getItems().setAll(Font.getFamilies().stream().filter(f->!f.toLowerCase().contains("symbol")).toList());fontChoice.setPromptText("Fuente");fontChoice.setOnAction(e->{if(fontChoice.getValue()!=null)editorCommand("fontName",fontChoice.getValue());});
        ColorPicker color=new ColorPicker(Color.BLACK);color.setPrefWidth(42);color.setTooltip(new Tooltip("Color de texto"));color.setOnAction(e->editorCommand("foreColor","#"+color.getValue().toString().substring(2,8)));
        ColorPicker highlight=new ColorPicker(Color.YELLOW);highlight.setPrefWidth(42);highlight.setTooltip(new Tooltip("Resaltar"));highlight.setOnAction(e->editorCommand("hiliteColor","#"+highlight.getValue().toString().substring(2,8)));
        Button undo=button("↶",()->editorCommand("undo")),redo=button("↷",()->editorCommand("redo"));undo.setTooltip(new Tooltip("Deshacer"));redo.setTooltip(new Tooltip("Rehacer"));
        MenuButton alignment=new MenuButton("",new FontIcon("fth-align-left"));alignment.getStyleClass().add("button-outlined");alignment.setTooltip(new Tooltip("Alineación"));for(String[] option:new String[][]{{"Alinear a la izquierda","justifyLeft","fth-align-left"},{"Centrar","justifyCenter","fth-align-center"},{"Alinear a la derecha","justifyRight","fth-align-right"},{"Justificar","justifyFull","fth-align-justify"}}){MenuItem item=new MenuItem(option[0],new FontIcon(option[2]));item.setOnAction(e->editorCommand(option[1]));alignment.getItems().add(item);}
        HBox home=new HBox(5,fontChoice,button("N",()->editorCommand("bold")),button("K",()->editorCommand("italic")),button("S",()->editorCommand("underline")),color,highlight,alignment,undo,redo);
        MenuButton headerMenu=new MenuButton("Encabezado y pie",new FontIcon("fth-file-text"));headerMenu.getStyleClass().add("button-outlined");for(String[] option:new String[][]{{"Agregar encabezado","header"},{"Imagen en encabezado","headerImage"},{"Agregar pie","footer"},{"Imagen en pie","footerImage"},{"Número de página","pageNumber"}}){MenuItem item=new MenuItem(option[0]);item.setOnAction(e->safe(()->{switch(option[1]){case "header"->addHeaderFooter(true);case "headerImage"->insertImage(true);case "footer"->addHeaderFooter(false);case "footerImage"->insertImageInFooter();case "pageNumber"->insertPageNumber();}}));headerMenu.getItems().add(item);}
        HBox insert=new HBox(5,button("Insertar variable",this::insertSelected),button("Insertar imagen",()->insertImage(false)),tableGrid(),button("Salto de página",this::insertPageBreak),headerMenu);
        paperChoice.getItems().setAll("A4","Carta");paperChoice.setValue("A4");paperChoice.setOnAction(e->{if(!readingPageSize)setPaperSize();});
        zoomChoice.getItems().setAll("Ancho","Página","75%","100%","125%","150%","200%");zoomChoice.setValue("Ancho");zoomChoice.setOnAction(e->{zoomMode=zoomChoice.getValue();updateZoom();});
        paletteToggle=button("Contraer variables",()->{boolean visible=!palettePanel.isVisible();palettePanel.setVisible(visible);palettePanel.setManaged(visible);paletteToggle.setText(visible?"Contraer variables":"Mostrar variables");Platform.runLater(this::updateZoom);});
        HBox view=new HBox(7,new Label("Papel"),paperChoice,zoomChoice,pageIndicator);
        TabPane tools=new TabPane();Map<String,Node> sections=new LinkedHashMap<>();sections.put("Inicio",home);sections.put("Insertar",insert);sections.put("Vista",view);for(var entry:sections.entrySet()){ScrollPane strip=new ScrollPane(entry.getValue());strip.setFitToHeight(true);strip.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);strip.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);Tab tab=new Tab(entry.getKey(),strip);tab.setClosable(false);tools.getTabs().add(tab);}tools.setMinHeight(82);tools.setPrefHeight(82);tools.setMaxHeight(82);tools.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(tools,Priority.ALWAYS);
        tableTools=new HBox(5);tableTools.getStyleClass().add("toolbar");tableTools.setVisible(false);tableTools.setManaged(false);MenuButton borders=new MenuButton("Bordes",new FontIcon("fth-grid"));borders.getStyleClass().add("button-outlined");for(String[] b:new String[][]{{"Todos los bordes","all"},{"Sin bordes","none"},{"Bordes exteriores","outer"},{"Bordes interiores","inner"}}){MenuItem item=new MenuItem(b[0]);item.setOnAction(e->tableAction("borders",b[1]));borders.getItems().add(item);}MenuButton borderStyle=new MenuButton("Estilo",new FontIcon("fth-pen-tool"));borderStyle.getStyleClass().add("button-outlined");for(String[] style:new String[][]{{"Sencillo","solid"},{"Doble","double"},{"Discontinuo","dashed"}}){MenuItem item=new MenuItem(style[0]);item.setOnAction(e->tableAction("borderStyle",style[1]));borderStyle.getItems().add(item);}ComboBox<String> thickness=new ComboBox<>();thickness.getItems().setAll("1","2","3");thickness.setValue("1");thickness.setPrefWidth(65);thickness.setOnAction(e->tableAction("borderWidth",thickness.getValue()));ColorPicker borderColor=new ColorPicker(Color.GRAY);borderColor.setPrefWidth(42);borderColor.setTooltip(new Tooltip("Color de bordes"));borderColor.setOnAction(e->tableAction("borderColor","#"+borderColor.getValue().toString().substring(2,8)));MenuButton remove=new MenuButton("Eliminar",new FontIcon("fth-trash-2"));remove.getStyleClass().add("button-outlined");for(String[] a:new String[][]{{"Eliminar fila","deleteRow"},{"Eliminar columna","deleteColumn"},{"Eliminar tabla completa","deleteTable"}}){MenuItem item=new MenuItem(a[0]);item.setOnAction(e->tableAction(a[1]));remove.getItems().add(item);}tableTools.getChildren().addAll(new Label("TABLA"),borders,borderStyle,thickness,borderColor,remove);
        imageTools=new HBox(5,new Label("IMAGEN"),button("Reducir",()->imageAction("smaller")),button("Ampliar",()->imageAction("larger")),button("Izquierda",()->imageAction("left")),button("Centrar",()->imageAction("center")),button("Derecha",()->imageAction("right")),button("Eliminar imagen",()->imageAction("delete")));imageTools.getStyleClass().add("toolbar");imageTools.setVisible(false);imageTools.setManaged(false);
        return new VBox(2,new HBox(6,paletteToggle,tools),tableTools,imageTools);
    }
    private void setLineSpacing(double spacing){if(selectedParagraph==null)return;selectedParagraph.setSpacingBetween(spacing);changed();}
    private void adjustParagraphSpacing(boolean before,int change){if(selectedParagraph==null)return;if(before)selectedParagraph.setSpacingBefore(Math.max(0,selectedParagraph.getSpacingBefore()+change));else selectedParagraph.setSpacingAfter(Math.max(0,selectedParagraph.getSpacingAfter()+change));changed();}
    private void adjustIndent(int change){if(selectedParagraph==null)return;selectedParagraph.setIndentationLeft(Math.max(0,selectedParagraph.getIndentationLeft()+change));changed();}
    private void setHeading(String style){if(selectedParagraph==null)return;selectedParagraph.setStyle(style);changed();}
    private void clearFormat(){if(selectedRun==null)return;selectedRun.setBold(false);selectedRun.setItalic(false);selectedRun.setUnderline(UnderlinePatterns.NONE);selectedRun.setStrikeThrough(false);selectedRun.setColor("000000");selectedRun.setTextHighlightColor("none");if(selectedRun.getCTR().isSetRPr()){var props=selectedRun.getCTR().getRPr();while(props.sizeOfShdArray()>0)props.removeShd(0);}changed();}
    private void adjustTable(boolean row,boolean add){if(selectedParagraph==null||!(selectedParagraph.getBody() instanceof XWPFTableCell cell))return;XWPFTable table=cell.getTableRow().getTable();if(row){if(add)table.createRow();else if(table.getNumberOfRows()>1)table.removeRow(table.getRows().indexOf(cell.getTableRow()));}else{int column=cell.getTableRow().getTableCells().indexOf(cell);for(XWPFTableRow r:table.getRows()){if(add)r.addNewTableCell();else if(r.getTableCells().size()>1)r.removeCell(Math.min(column,r.getTableCells().size()-1));}}changed();}
    private void changed(){try{syncEditor();}catch(Exception ex){error(ex);return;}dirty=true;status.setText("Cambios sin guardar");renderDocument();}
    private void updateZoom(){double viewport=richEditor.getWidth();if(viewport<=0||!editorReady)return;double scale=switch(zoomMode){case "Página"->Math.min((viewport-56)/pageWidth,(richEditor.getHeight()-80)/pageHeight);case "Ancho"->(viewport-56)/pageWidth;default->Double.parseDouble(zoomMode.replace("%",""))/100.0;};scale=Math.max(.25,Math.min(3,scale));richEditor.getEngine().executeScript("window.studio.zoom("+scale+")");updatePageIndicator();}
    private void updatePageIndicator(){if(!editorReady)return;try{int current=((Number)richEditor.getEngine().executeScript("window.studio.currentPage()")).intValue(),count=((Number)richEditor.getEngine().executeScript("window.studio.pageCount()")).intValue();pageIndicator.setText("Página "+Math.min(current,count)+" de "+count);}catch(Exception ignored){}}
    private void setPaperSize(){if(document==null)return;pageWidth="Carta".equals(paperChoice.getValue())?816:794;pageHeight="Carta".equals(paperChoice.getValue())?1056:1123;var sect=document.getDocument().getBody().isSetSectPr()?document.getDocument().getBody().getSectPr():document.getDocument().getBody().addNewSectPr();var size=sect.isSetPgSz()?sect.getPgSz():sect.addNewPgSz();size.setW(java.math.BigInteger.valueOf(Math.round(pageWidth*15)));size.setH(java.math.BigInteger.valueOf(Math.round(pageHeight*15)));changed();}
    private void toggleOrientation(){if(document==null)return;double w=pageWidth;pageWidth=pageHeight;pageHeight=w;var sect=document.getDocument().getBody().isSetSectPr()?document.getDocument().getBody().getSectPr():document.getDocument().getBody().addNewSectPr();var size=sect.isSetPgSz()?sect.getPgSz():sect.addNewPgSz();size.setW(java.math.BigInteger.valueOf(Math.round(pageWidth*15)));size.setH(java.math.BigInteger.valueOf(Math.round(pageHeight*15)));changed();}
    private void setMargins(int twips){if(document==null)return;var sect=document.getDocument().getBody().isSetSectPr()?document.getDocument().getBody().getSectPr():document.getDocument().getBody().addNewSectPr();var margin=sect.isSetPgMar()?sect.getPgMar():sect.addNewPgMar();long n=twips;margin.setLeft(java.math.BigInteger.valueOf(n));margin.setRight(java.math.BigInteger.valueOf(n));margin.setTop(java.math.BigInteger.valueOf(n));margin.setBottom(java.math.BigInteger.valueOf(n));changed();}
    private void insertTable(){if(document==null)return;document.createTable(2,2);changed();}
    private void insertPageBreak(){if(document==null)return;XWPFParagraph p=document.createParagraph();p.setPageBreak(true);changed();}
    private void addHeaderFooter(boolean header){if(document==null)return;XWPFHeaderFooter part=header?(document.getHeaderList().isEmpty()?document.createHeader(HeaderFooterType.DEFAULT):document.getHeaderList().get(0)):(document.getFooterList().isEmpty()?document.createFooter(HeaderFooterType.DEFAULT):document.getFooterList().get(0));part.createParagraph().createRun().setText(header?"Encabezado":"Pie de página");changed();}
    private void insertPageNumber(){if(document==null)return;XWPFFooter footer=document.getFooterList().isEmpty()?document.createFooter(HeaderFooterType.DEFAULT):document.getFooterList().get(0);XWPFRun run=footer.createParagraph().createRun();run.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN);XWPFRun instruction=footer.getParagraphs().get(footer.getParagraphs().size()-1).createRun();instruction.getCTR().addNewInstrText().setStringValue(" PAGE ");XWPFRun end=footer.getParagraphs().get(footer.getParagraphs().size()-1).createRun();end.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);changed();}
    private void insertImage(boolean header){if(document==null)return;FileChooser chooser=new FileChooser();chooser.setTitle("Insertar imagen");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG/JPG","*.png","*.jpg","*.jpeg"));File file=chooser.showOpenDialog(stage);if(file==null)return;try(InputStream in=Files.newInputStream(file.toPath())){Image source=new Image(file.toURI().toString());if(source.isError()||source.getWidth()<=0)throw new IOException("No se pudo leer la imagen.");double width=Math.min(300,source.getWidth()*72/96),height=width*source.getHeight()/source.getWidth();if(height>600){width*=600/height;height=600;}if(!header&&editorReady){String mime=file.getName().toLowerCase().endsWith(".png")?"image/png":"image/jpeg";String uri="data:"+mime+";base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));if(Boolean.TRUE.equals(richEditor.getEngine().executeScript("window.studio.insertImage("+jsonString(uri)+","+Math.round(width*96/72)+","+Math.round(height*96/72)+")")))return;throw new IllegalArgumentException("Coloque el cursor en el documento antes de insertar la imagen.");}XWPFHeader h=document.getHeaderList().isEmpty()?document.createHeader(HeaderFooterType.DEFAULT):document.getHeaderList().get(0);XWPFParagraph p=h.createParagraph();int type=file.getName().toLowerCase().endsWith(".png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;p.createRun().addPicture(in,type,file.getName(),Units.toEMU(width),Units.toEMU(height));changed();}catch(Exception e){error(e);}}
    private void insertImageInFooter(){if(document==null)return;FileChooser chooser=new FileChooser();chooser.setTitle("Imagen en pie de página");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG/JPG","*.png","*.jpg","*.jpeg"));File file=chooser.showOpenDialog(stage);if(file==null)return;try(InputStream in=Files.newInputStream(file.toPath())){Image source=new Image(file.toURI().toString());if(source.isError()||source.getWidth()<=0)throw new IOException("No se pudo leer la imagen.");double width=Math.min(300,source.getWidth()*72/96),height=width*source.getHeight()/source.getWidth();XWPFFooter footer=document.getFooterList().isEmpty()?document.createFooter(HeaderFooterType.DEFAULT):document.getFooterList().get(0);int type=file.getName().toLowerCase().endsWith(".png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;footer.createParagraph().createRun().addPicture(in,type,file.getName(),Units.toEMU(width),Units.toEMU(height));changed();}catch(Exception ex){error(ex);}}
    private void resizePicture(XWPFRun run,XWPFPicture picture,double factor){var inline=run.getCTR().getDrawingArray(0).getInlineArray(0);if(inline.getExtent()==null)return;long width=Math.max(Units.toEMU(20),Math.round(inline.getExtent().getCx()*factor));long height=Math.max(Units.toEMU(20),Math.round(inline.getExtent().getCy()*factor));inline.getExtent().setCx(width);inline.getExtent().setCy(height);var shape=picture.getCTPicture().getSpPr();if(shape!=null&&shape.isSetXfrm()&&shape.getXfrm().isSetExt()){shape.getXfrm().getExt().setCx(width);shape.getXfrm().getExt().setCy(height);}changed();}
    private boolean insertToken(TextField field,String reference){
        if(currentTemplate==null){saved("Primero cree o importe una plantilla");return false;}
        try{
            String key=reference;
            if(reference.startsWith("definition:")){
                long id=Long.parseLong(reference.substring("definition:".length()));
                Definition d=store.definitions().stream().filter(x->x.id()==id).findFirst().orElseThrow(()->new IllegalArgumentException("Variable inexistente."));key=d.key();
                Variable bound=store.variables(currentTemplate.id()).stream().filter(v->v.key().equals(d.key())).findFirst().orElse(null);
                if(bound==null)store.bindDefinition(currentTemplate.id(),id,false,store.variables(currentTemplate.id()).size()+1);
                else if(store.definitionId(bound.id())!=id)throw new IllegalArgumentException("Esta plantilla ya utiliza '"+key+"' con otra definición. Use otro identificador.");
            }
            field.insertText(field.getCaretPosition(),"{{"+key+"}}");renderDocument();dirty=true;saved("Variable insertada · Guarde la plantilla");return true;
        }catch(Exception e){error(e);return false;}
    }

    private void renderDocument(){selectedText=null;if(document==null)return;
        pageWidth=794;pageHeight=1123;var sect=document.getDocument().getBody().getSectPr();if(sect!=null&&sect.isSetPgSz()){pageWidth=((Number)sect.getPgSz().getW()).doubleValue()/15.0;pageHeight=((Number)sect.getPgSz().getH()).doubleValue()/15.0;}readingPageSize=true;paperChoice.setValue(Math.abs(pageWidth-816)<15?"Carta":"A4");readingPageSize=false;
        double left=sect!=null&&sect.isSetPgMar()&&sect.getPgMar().getLeft()!=null?((Number)sect.getPgMar().getLeft()).doubleValue()/15.0:72;
        double right=sect!=null&&sect.isSetPgMar()&&sect.getPgMar().getRight()!=null?((Number)sect.getPgMar().getRight()).doubleValue()/15.0:left;
        double top=sect!=null&&sect.isSetPgMar()&&sect.getPgMar().getTop()!=null?((Number)sect.getPgMar().getTop()).doubleValue()/15.0:72;
        double bottom=sect!=null&&sect.isSetPgMar()&&sect.getPgMar().getBottom()!=null?((Number)sect.getPgMar().getBottom()).doubleValue()/15.0:top;
        richModel=new ContinuousDocxEditor(document);
        try(InputStream in=Objects.requireNonNull(getClass().getResourceAsStream("/editor/document-editor.html"))){String shell=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);String html=shell.replace("__DOCUMENT_CONTENT__",richModel.html());editorLeft=left;editorRight=right;editorTop=top;editorBottom=bottom;editorReady=false;richEditor.getEngine().loadContent(html);}catch(Exception ex){error(ex);}
    }
    private VBox newPage(double margin,double top){VBox page=new VBox(5);page.getStyleClass().add("paper");page.setPrefSize(pageWidth,pageHeight);page.setMinSize(pageWidth,pageHeight);page.setMaxWidth(pageWidth);page.setPadding(new Insets(top,margin,Math.min(top,72),margin));VBox body=new VBox(5);for(XWPFHeader header:document.getHeaderList())for(XWPFParagraph p:header.getParagraphs())page.getChildren().add(renderParagraph(p,true));page.getChildren().add(body);Region spacer=new Region();VBox.setVgrow(spacer,Priority.ALWAYS);page.getChildren().add(spacer);for(XWPFFooter footer:document.getFooterList())for(XWPFParagraph p:footer.getParagraphs())page.getChildren().add(renderParagraph(p,true));paper.getChildren().add(page);return body;}
    private double estimateHeight(Node node){if(node instanceof FlowPane line){double width=Math.max(100,pageWidth-150);double length=line.getChildren().stream().mapToDouble(n->n instanceof TextField f?f.getPrefWidth():n instanceof ImageView i?i.getFitWidth():70).sum();return Math.max(30,Math.ceil(length/width)*28);}return 40;}
    private Node renderParagraph(XWPFParagraph p,boolean editable){FlowPane line=new FlowPane(2,3);line.getStyleClass().add("doc-paragraph");line.setAlignment(switch(p.getAlignment()){case CENTER->Pos.CENTER;case RIGHT->Pos.CENTER_RIGHT;default->Pos.CENTER_LEFT;});
        if(p.getRuns().isEmpty()&&editable)p.createRun().setText("");
        for(XWPFRun run:p.getRuns()){
            String raw=run.getText(0);if(raw!=null){Matcher m=TOKEN.matcher(raw);int at=0;while(m.find()){if(m.start()>at)addTextSegment(line,run,p,raw.substring(at,m.start()),editable);addToken(line,run,m.group(1),editable,m.start());at=m.end();}if(at<raw.length()||raw.isEmpty())addTextSegment(line,run,p,raw.substring(at),editable);}
            for(XWPFPicture picture:run.getEmbeddedPictures()){try{ImageView image=new ImageView(new Image(new ByteArrayInputStream(picture.getPictureData().getData())));image.setPreserveRatio(true);image.setFitWidth(Math.min(pageWidth-160,Math.max(80,picture.getWidth()*96/72)));if(editable&&run.getEmbeddedPictures().size()==1&&run.getCTR().sizeOfDrawingArray()==1&&run.getCTR().getDrawingArray(0).sizeOfInlineArray()==1){MenuItem smaller=new MenuItem("Reducir a 75 %"),larger=new MenuItem("Ampliar a 125 %"),remove=new MenuItem("Eliminar imagen");smaller.setOnAction(e->resizePicture(run,picture,.75));larger.setOnAction(e->resizePicture(run,picture,1.25));remove.setOnAction(e->{run.getCTR().removeDrawing(0);changed();});image.setOnContextMenuRequested(e->new ContextMenu(smaller,larger,remove).show(image,e.getScreenX(),e.getScreenY()));}line.getChildren().add(image);}catch(Exception ignored){line.getChildren().add(new Label("[Imagen no previsualizable]"));}}
        }
        if(line.getChildren().isEmpty())line.getChildren().add(new Label(" "));return line;}
    private void addTextSegment(FlowPane line,XWPFRun run,XWPFParagraph p,String text,boolean editable){
        if(!editable){Text t=new Text(text);t.setFont(Font.font(run.getFontFamily()==null?"Serif":run.getFontFamily(),run.isBold()?FontWeight.BOLD:FontWeight.NORMAL,run.getFontSize()>0?run.getFontSize():13));line.getChildren().add(t);return;}
        TextField field=new TextField(text);field.getStyleClass().add("run-field");field.setPrefWidth(Math.max(28,Math.min(700,text.length()*8+25)));field.setStyle("-fx-font-family:'"+(run.getFontFamily()==null?"Serif":run.getFontFamily())+"';-fx-font-size:"+(run.getFontSize()>0?run.getFontSize():13)+"px;"+(run.isBold()?"-fx-font-weight:bold;":"")+(run.isItalic()?"-fx-font-style:italic;":"")+(run.getColor()!=null?"-fx-text-fill:#"+run.getColor()+";":""));
        field.focusedProperty().addListener((o,a,b)->{if(b){selectedRun=run;selectedParagraph=p;selectedText=field;}});
        field.textProperty().addListener((o,a,b)->{field.setPrefWidth(Math.max(28,Math.min(700,b.length()*8+25)));StringBuilder all=new StringBuilder();for(Node n:line.getChildren()){if(n.getUserData()==run){if(n instanceof TextField f)all.append(f.getText());else if(n instanceof Label l)all.append("{{").append(n.getProperties().get("key")).append("}}");}}run.setText(all.toString(),0);dirty=true;status.setText("Cambios sin guardar");});
        field.setUserData(run);field.setOnDragOver(e->{if(e.getDragboard().hasString())e.acceptTransferModes(TransferMode.COPY,TransferMode.MOVE);e.consume();});field.setOnDragDropped(e->{String key=e.getDragboard().getString();if(key!=null){if(field.getSkin() instanceof TextFieldSkin skin)field.positionCaret(skin.getIndex(e.getX(),e.getY()).getInsertionIndex());dragTargetRun=run;dragTargetOffset=runOffset(line,field)+field.getCaretPosition();e.setDropCompleted(insertToken(field,key));}e.consume();});
        line.getChildren().add(field);
    }
    private int runOffset(FlowPane line,Node target){int offset=0;for(Node n:line.getChildren()){if(n==target)break;if(n.getUserData()==target.getUserData()){if(n instanceof TextField f)offset+=f.getText().length();else if(n instanceof Label l)offset+=String.valueOf(l.getProperties().get("key")).length()+4;}}return offset;}
    private void addToken(FlowPane line,XWPFRun run,String key,boolean editable,int offset){Label chip=new Label("⟦"+key+"⟧");chip.getStyleClass().add("token");chip.setUserData(run);chip.getProperties().put("key",key);chip.setTooltip(new Tooltip("{{"+key+"}} · Arrastre, copie o elimine esta aparición"));if(editable){chip.setOnDragDetected(e->{dragSourceRun=run;dragSourceToken="{{"+key+"}}";dragSourceOffset=runOffset(line,chip);dragTargetRun=null;Dragboard db=chip.startDragAndDrop(TransferMode.MOVE);ClipboardContent cc=new ClipboardContent();cc.putString(key);db.setContent(cc);e.consume();});chip.setOnDragDone(e->{if(e.getTransferMode()==TransferMode.MOVE&&dragSourceRun!=null){String txt=dragSourceRun.getText(0);int pos=dragSourceOffset+(dragSourceRun==dragTargetRun&&dragTargetOffset<=dragSourceOffset?dragSourceToken.length():0);if(txt!=null&&pos>=0&&pos+dragSourceToken.length()<=txt.length()&&txt.startsWith(dragSourceToken,pos))dragSourceRun.setText(txt.substring(0,pos)+txt.substring(pos+dragSourceToken.length()),0);dirty=true;renderDocument();}dragSourceRun=null;dragTargetRun=null;e.consume();});MenuItem copy=new MenuItem("Copiar variable"),cut=new MenuItem("Cortar variable"),delete=new MenuItem("Eliminar aparición");copy.setOnAction(e->copyToken(key));cut.setOnAction(e->{copyToken(key);removeToken(line,chip,run,key);});delete.setOnAction(e->removeToken(line,chip,run,key));chip.setContextMenu(new ContextMenu(copy,cut,delete));chip.setOnMouseClicked(e->{if(e.getClickCount()==2)removeToken(line,chip,run,key);});}line.getChildren().add(chip);}
    private void copyToken(String key){ClipboardContent content=new ClipboardContent();content.putString("{{"+key+"}}");Clipboard.getSystemClipboard().setContent(content);}
    private void removeToken(FlowPane line,Node chip,XWPFRun run,String key){String txt=run.getText(0);int at=runOffset(line,chip);if(txt!=null&&txt.startsWith("{{"+key+"}}",at)){run.setText(txt.substring(0,at)+txt.substring(at+key.length()+4),0);changed();}}
    private void styleRun(String action){if(selectedRun==null)return;switch(action){case "bold"->selectedRun.setBold(!selectedRun.isBold());case "italic"->selectedRun.setItalic(!selectedRun.isItalic());case "underline"->selectedRun.setUnderline(selectedRun.getUnderline()==UnderlinePatterns.NONE?UnderlinePatterns.SINGLE:UnderlinePatterns.NONE);case "strike"->selectedRun.setStrikeThrough(!selectedRun.isStrikeThrough());case "smaller"->selectedRun.setFontSize(Math.max(8,selectedRun.getFontSize()-1));case "larger"->selectedRun.setFontSize((selectedRun.getFontSize()>0?selectedRun.getFontSize():12)+1);}changed();}
    private void align(ParagraphAlignment a){if(selectedParagraph==null)return;selectedParagraph.setAlignment(a);dirty=true;renderDocument();}
    private void addParagraph(){if(document==null)return;document.createParagraph().createRun().setText("");dirty=true;renderDocument();}

    private BorderPane catalogPage;
    private void buildCatalog(){catalogPage=new BorderPane();catalogPage.getStyleClass().add("page");VBox left=new VBox(8,kindSearch,kindList,button("+ Nuevo tipo",this::newKind),button("Duplicar",this::duplicateKind),button("Eliminar",this::deleteKind));left.getStyleClass().add("side-panel");left.setPrefWidth(250);VBox.setVgrow(kindList,Priority.ALWAYS);kindSearch.setPromptText("Buscar título o categoría");kindSearch.textProperty().addListener((o,a,b)->refreshKinds());kindList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->loadKind(b));catalogPage.setLeft(left);
        kindDescription.setPrefRowCount(3);fieldList.setPrefHeight(230);kindActive.setSelected(true);
        HBox order=new HBox(8,button("↑",()->moveField(-1)),button("↓",()->moveField(1)),button("Quitar campo",()->{Variable v=fieldList.getSelectionModel().getSelectedItem();if(v!=null)fieldList.getItems().remove(v);}));
        HBox addField=new HBox(8,addFieldChoice,button("Añadir campo",()->{Variable v=addFieldChoice.getValue();if(v!=null&&fieldList.getItems().stream().noneMatch(x->x.key().equals(v.key())))fieldList.getItems().add(v);}));
        fieldList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{loadingFieldRequired=true;fieldRequired.setDisable(b==null);fieldDefault.setDisable(b==null);fieldRequired.setSelected(b!=null&&kindRequiredChoices.getOrDefault(b.key(),b.required()));fieldDefault.setText(b==null?"":kindDefaultChoices.getOrDefault(b.key(),""));loadingFieldRequired=false;});
        fieldRequired.setDisable(true);fieldRequired.setOnAction(e->{Variable v=fieldList.getSelectionModel().getSelectedItem();if(v!=null&&!loadingFieldRequired)kindRequiredChoices.put(v.key(),fieldRequired.isSelected());});
        fieldDefault.setDisable(true);fieldDefault.setPromptText("Valor predeterminado solo para este documento");fieldDefault.textProperty().addListener((o,a,b)->{Variable v=fieldList.getSelectionModel().getSelectedItem();if(v!=null&&!loadingFieldRequired)kindDefaultChoices.put(v.key(),b);});
        bindingGroup.setPromptText("Ej.: persona_1");bindingKind.getItems().setAll("Persona","Empresa");bindingKind.setValue("Persona");bindingKind.setOnAction(e->refreshBindingFields());refreshBindingFields();bindingList.setPrefHeight(150);
        HBox bindingRow=new HBox(8,bindingGroup,bindingKind,bindingField,bindingVariable);bindingGroup.setPrefWidth(130);bindingField.setPrefWidth(165);bindingVariable.setPrefWidth(190);
        VBox form=new VBox(12,new Label("CONFIGURAR GENERACIÓN"),labelled("Título",kindTitle),labelled("Categoría opcional",kindCategory),labelled("Descripción opcional",kindDescription),labelled("Plantilla",kindTemplate),kindActive,new Label("CAMPOS DEL FORMULARIO · Ordene con ↑ ↓"),fieldList,fieldRequired,labelled("Valor predeterminado del campo seleccionado",fieldDefault),order,addField,button("Guardar documento",this::saveKind),new Separator(),new Label("VINCULACIÓN LOCAL DE PERSONAS Y EMPRESAS"),new Label("Cree un grupo por cada registro independiente. La generación guardará datos asociados con documento válido."),bindingRow,button("Añadir asociación",this::saveContactBinding),bindingList,button("Eliminar asociación",this::deleteContactBinding));form.getStyleClass().add("catalog-form");ScrollPane scroll=new ScrollPane(form);scroll.setFitToWidth(true);catalogPage.setCenter(scroll);
        kindTemplate.setOnAction(e->loadFieldsForTemplate());
    }
    private void showCatalog(){showEditor();refreshKinds();editorTabs.getSelectionModel().select(1);}
    private void refreshKinds(){
        if(refreshingGenerator)return;refreshingGenerator=true;
        try{
            String q=kindSearch.getText().toLowerCase(Locale.ROOT);kindList.getItems().setAll(store.kinds().stream().filter(k->k.title().toLowerCase(Locale.ROOT).contains(q)||(k.category()!=null&&k.category().toLowerCase(Locale.ROOT).contains(q))).toList());
            Template selected=generateTemplate.getValue();TemplateGroup group=generateGroup.getValue();String search=generateSearch.getText().trim();List<Template> choices=new ArrayList<>();
            if(group!=null)for(Template template:store.templates())if(store.inGroup(template.id(),group.id())&&libraryMatches(template.name(),search))choices.add(template);
            generateTemplate.getItems().setAll(choices);
            Template restored=selected==null?null:choices.stream().filter(t->t.id()==selected.id()).findFirst().orElse(null);generateTemplate.getSelectionModel().select(restored);
            if(restored==null){historicalTemplate=null;buildDynamicForm();}
        }catch(Exception e){error(e);}finally{refreshingGenerator=false;}
    }



    private List<Variable> variablesForTemplate(Template t)throws Exception{List<Variable> result=new ArrayList<>(store.variables(t.id()));Set<String> known=new HashSet<>();for(Variable v:result)known.add(v.key());Set<String> markers;try(XWPFDocument doc=DocumentEngine.open(t.edited())){markers=DocumentEngine.markers(doc);}for(Definition d:store.definitions())if(markers.contains(d.key())&&known.add(d.key()))result.add(new Variable(-d.id(),t.id(),d.key(),d.label(),d.type(),d.defaultValue(),false,result.size()+1));return result.stream().filter(v->markers.contains(v.key())).toList();}
    private void newKind(){currentKind=null;kindRequiredChoices.clear();kindDefaultChoices.clear();kindList.getSelectionModel().clearSelection();kindTitle.clear();kindCategory.clear();kindDescription.clear();kindActive.setSelected(true);kindTemplate.setValue(currentTemplate);fieldList.getItems().clear();bindingList.getItems().clear();}
    private void loadKind(Kind k){if(k==null)return;currentKind=k;kindTitle.setText(k.title());kindCategory.setText(k.category());kindDescription.setText(k.description());kindActive.setSelected(k.active());kindTemplate.getSelectionModel().select(kindTemplate.getItems().stream().filter(t->t.id()==k.templateId()).findFirst().orElse(null));loadFieldsForTemplate();refreshContactBindings();try{kindRequiredChoices.putAll(store.kindRequirements(k.id()));kindDefaultChoices.clear();kindDefaultChoices.putAll(store.kindDefaults(k.id()));}catch(Exception e){error(e);}Map<String,Variable> vars=new HashMap<>();for(Variable v:addFieldChoice.getItems())vars.put(v.key(),v);List<Variable> ordered=new ArrayList<>();for(String key:k.fields().split(",")){Variable v=vars.get(key);if(v!=null)ordered.add(v);}fieldList.getItems().setAll(ordered);}
    private void loadFieldsForTemplate(){Template t=kindTemplate.getValue();try{kindRequiredChoices.clear();kindDefaultChoices.clear();List<Variable> vars;if(t==null)vars=List.of();else{Set<String> used;try(XWPFDocument doc=DocumentEngine.open(t.edited())){used=DocumentEngine.markers(doc);}vars=store.variables(t.id()).stream().filter(v->used.contains(v.key())).toList();}fieldList.getItems().setAll(vars);addFieldChoice.getItems().setAll(vars);bindingVariable.getItems().setAll(vars);refreshContactBindings();}catch(Exception e){error(e);}}
    private void refreshBindingFields(){bindingField.getItems().setAll("Empresa".equals(bindingKind.getValue())?List.of("Razón social","RUC","Domicilio fiscal","Representantes"):List.of("Nombres","Apellidos","Tipo de documento","Número de documento","Domicilio"));bindingField.getSelectionModel().selectFirst();}
    private void refreshContactBindings(){Template t=kindTemplate.getValue();try{bindingList.getItems().setAll(t==null?List.of():data.contactBindings(t.id()));}catch(Exception e){error(e);}}
    private void saveContactBinding(){Template t=kindTemplate.getValue();Variable v=bindingVariable.getValue();if(t==null||v==null||bindingGroup.getText().isBlank())throw new IllegalArgumentException("Seleccione plantilla, grupo, dato y variable.");try{String field=switch(bindingField.getValue()){case "Nombres","Razón social"->"given";case "Apellidos"->"family";case "Tipo de documento"->"document_type";case "Número de documento","RUC"->"number";case "Domicilio","Domicilio fiscal"->"address";case "Representantes"->"representatives";default->throw new IllegalArgumentException("Dato no admitido.");};data.saveContactBinding(new ContactBinding(0,t.id(),bindingGroup.getText().trim(),bindingKind.getValue(),field,v.key()));refreshContactBindings();saved("Asociación local guardada");}catch(Exception e){error(e);}}
    private void deleteContactBinding(){ContactBinding b=bindingList.getSelectionModel().getSelectedItem();if(b==null)return;try{data.deleteContactBinding(b.id());refreshContactBindings();saved("Asociación eliminada");}catch(Exception e){error(e);}}
    private void moveField(int delta){int i=fieldList.getSelectionModel().getSelectedIndex();int j=i+delta;if(i<0||j<0||j>=fieldList.getItems().size())return;Collections.swap(fieldList.getItems(),i,j);fieldList.getSelectionModel().select(j);}
    private void saveKind(){Template t=kindTemplate.getValue();if(t==null||kindTitle.getText().isBlank())throw new IllegalArgumentException("Título y plantilla son obligatorios.");try{Set<String> markers;try(XWPFDocument doc=DocumentEngine.open(t.edited())){markers=DocumentEngine.markers(doc);}Map<String,Variable> bound=new HashMap<>();for(Variable v:store.variables(t.id()))bound.put(v.key(),v);Set<String> shown=new HashSet<>();for(Variable v:fieldList.getItems())shown.add(v.key());for(String marker:markers){Variable v=bound.get(marker);if(v==null)throw new IllegalArgumentException("Variable sin vincular en la plantilla: "+marker);if(!shown.contains(marker)&&(kindDefaultChoices.getOrDefault(marker,v.defaultValue())==null||kindDefaultChoices.getOrDefault(marker,v.defaultValue()).isBlank()))throw new IllegalArgumentException("La variable '"+marker+"' no está en el formulario ni tiene valor predeterminado.");}String fields=String.join(",",fieldList.getItems().stream().map(Variable::key).toList());Kind k=store.saveKind(new Kind(currentKind==null?0:currentKind.id(),kindTitle.getText().trim(),kindCategory.getText().trim(),kindDescription.getText().trim(),t.id(),fields,kindActive.isSelected()));Map<String,Boolean> requirements=new HashMap<>();for(Variable v:fieldList.getItems())requirements.put(v.key(),kindRequiredChoices.getOrDefault(v.key(),v.required()));store.saveKindFieldRules(k.id(),requirements,kindDefaultChoices);currentKind=k;refreshKinds();kindList.getSelectionModel().select(k);saved("Tipo documental guardado");}catch(Exception e){error(e);}}
    private void duplicateKind(){Kind k=kindList.getSelectionModel().getSelectedItem();if(k==null)return;try{Kind copy=store.saveKind(new Kind(0,k.title()+" (copia)",k.category(),k.description(),k.templateId(),k.fields(),k.active()));store.saveKindFieldRules(copy.id(),store.kindRequirements(k.id()),store.kindDefaults(k.id()));refreshKinds();saved("Tipo duplicado");}catch(Exception e){error(e);}}
    private void deleteKind(){Kind k=kindList.getSelectionModel().getSelectedItem();if(k==null)return;try{store.deleteKind(k.id());newKind();refreshKinds();saved("Tipo eliminado");}catch(Exception e){error(e);}}

    private BorderPane generatorPage;
    private void buildGenerator(){
        generatorPage=new BorderPane();generatorPage.getStyleClass().add("page");
        GridPane top=new GridPane();top.setHgap(10);for(double width:new double[]{24,46,30}){ColumnConstraints col=new ColumnConstraints();col.setPercentWidth(width);top.getColumnConstraints().add(col);}
        generateGroup.setOnAction(e->{if(!refreshingGenerator)refreshKinds();});generateTemplate.setOnAction(e->{if(!refreshingGenerator)safe(()->{historicalTemplate=null;buildDynamicForm();});});generateSearch.setPromptText("Buscar entre plantillas");generateSearch.textProperty().addListener((o,a,b)->refreshKinds());
        top.add(labelled("Grupo",generateGroup),0,0);top.add(labelled("Documento o plantilla",generateTemplate),1,0);top.add(labelled("Buscar",generateSearch),2,0);top.getStyleClass().add("toolbar");generatorPage.setTop(top);BorderPane.setMargin(top,new Insets(0,0,12,0));
        generatedForm.getStyleClass().add("generator-form");ScrollPane formScroll=scrolling(generatedForm);
        ScrollPane previewScroll=scrolling(preview);preview.getStyleClass().add("preview");previewScroll.setFitToHeight(true);
        GridPane actions=new GridPane();actions.setHgap(8);actions.setVgap(8);actions.getStyleClass().addAll("action-bar","generator-actions");
        for(int i=0;i<2;i++){ColumnConstraints col=new ColumnConstraints();col.setPercentWidth(50);actions.getColumnConstraints().add(col);}
        int actionIndex=0;for(Button action:List.of(button("Previsualizar",this::makePreview),button("Guardar DOCX",this::saveGenerated),button("Guardar PDF",this::savePdf),button("Abrir en Word",this::openGeneratedInWord),button("Imprimir",this::printGenerated))){action.setGraphic(null);action.setMinWidth(0);action.setMaxWidth(Double.MAX_VALUE);action.setWrapText(true);actions.add(action,actionIndex%2,actionIndex/2);actionIndex++;}
        VBox right=new VBox(10,new Label("VISTA PREVIA"),previewScroll,actions);right.setMinWidth(0);right.getStyleClass().add("preview-panel");VBox.setVgrow(previewScroll,Priority.ALWAYS);
        GridPane columns=new GridPane();columns.setHgap(14);ColumnConstraints form=new ColumnConstraints(),result=new ColumnConstraints();form.setPercentWidth(56);result.setPercentWidth(44);columns.getColumnConstraints().addAll(form,result);RowConstraints row=new RowConstraints();row.setVgrow(Priority.ALWAYS);columns.getRowConstraints().add(row);columns.add(formScroll,0,0);columns.add(right,1,0);GridPane.setVgrow(formScroll,Priority.ALWAYS);GridPane.setVgrow(right,Priority.ALWAYS);generatorPage.setCenter(columns);
    }
    private void showGenerator(){
        refreshingGenerator=true;
        try{
            TemplateGroup previous=generateGroup.getValue();List<TemplateGroup> groups=store.groups();generateGroup.getItems().setAll(groups);
            generateGroup.getSelectionModel().select(groups.stream().filter(g->previous!=null&&g.id()==previous.id()).findFirst().orElse(groups.isEmpty()?null:groups.get(0)));
        }catch(Exception e){error(e);}finally{refreshingGenerator=false;}
        refreshKinds();page("Generar documentos",generatorPage);
        if(generateTemplate.getValue()==null)generatedForm.getChildren().setAll(new Label(generateGroup.getItems().isEmpty()?"Cree un grupo y asocie sus plantillas desde Plantillas.":"Seleccione una plantilla del grupo."));
    }
    private void buildDynamicForm(){
        lookupDelays.values().forEach(PauseTransition::stop);lookupDelays.clear();
        Template template=generateTemplate.getValue();generatedBytes=null;generatedPdf=null;generatedHistoryId=0;preview.getChildren().clear();inputs.clear();formVariables.clear();manuallyEdited.clear();localValueOverrides.clear();generatedForm.getChildren().clear();
        if(template==null)return;
        try{
            List<Variable> variables=variablesForTemplate(template);GridPane grid=twoColumns();generatedForm.getChildren().add(grid);int slot=0;
            for(Variable variable:variables){
                String initial=VariableInput.initialValue(variable.type(),variable.defaultValue());Control input;
                if(List.of("Texto largo","Domicilio").contains(variable.type())){TextArea area=new TextArea(initial);area.setPrefRowCount(3);area.setWrapText(true);input=area;}
                else if("Fecha".equals(variable.type())){DatePicker date=new DatePicker();try{date.setValue(LocalDate.parse(initial));}catch(Exception ignored){date.setValue(LocalDate.now());}input=date;}
                else input=new TextField(initial);
                if(input instanceof TextInputControl editable)editable.textProperty().addListener((o,a,b)->{if(!applyingApi)manuallyEdited.add(variable.key());});
                else if(input instanceof DatePicker date)date.valueProperty().addListener((o,a,b)->{if(!applyingApi)manuallyEdited.add(variable.key());});
                inputs.put(variable.key(),input);formVariables.put(variable.key(),variable);Node control=input;
                String service=queryService(template.id(),variable);
                if(service!=null&&input instanceof TextField field){
                    PauseTransition delay=new PauseTransition(Duration.millis(350));lookupDelays.put(variable.key(),delay);
                    Runnable consult=()->{delay.stop();lookupForField(template,variable,field,service);};
                    delay.setOnFinished(e->{if(inputs.get(variable.key())!=field||!validQueryNumber(service,field.getText()))return;if(apiQueryRunning)delay.playFromStart();else safe(consult);});
                    field.textProperty().addListener((o,a,b)->{
                        delay.stop();if(applyingApi||!validQueryNumber(service,b))return;
                        try{resetContactEdits(template,variable,service);if(!loadLocalForField(template,variable,field,service))delay.playFromStart();}catch(Exception ex){error(ex);}
                    });
                    Button lookup=button("Consultar API",consult);lookup.setTooltip(new Tooltip("Busca primero en Clientes locales; si no existe, consulta la API."));field.setMinWidth(0);
                    lookup.setGraphic(null);lookup.getStyleClass().add("lookup-button");HBox query=new HBox(6,field,lookup);HBox.setHgrow(field,Priority.ALWAYS);control=query;
                }
                VBox field=labelled(variable.label()+(variable.required()?" *":""),control);
                if(input instanceof TextArea){if(slot%2!=0)slot++;grid.add(field,0,slot/2,2,1);slot+=2;}
                else{grid.add(field,slot%2,slot/2);slot++;}
            }
            addContactSelectors(template);if(inputs.isEmpty())generatedForm.getChildren().add(new Label("Esta plantilla no contiene variables de formulario."));
        }catch(Exception e){error(e);}
    }
    private void addContactSelectors(Template template)throws Exception{
        Map<String,List<ContactBinding>> groups=new LinkedHashMap<>();for(ContactBinding binding:data.contactBindings(template.id()))groups.computeIfAbsent(binding.groupKey(),ignored->new ArrayList<>()).add(binding);
        for(var entry:groups.entrySet()){
            List<ContactBinding> group=entry.getValue();String kind=group.get(0).kind();TextField search=new TextField();search.setPromptText("Buscar por nombre o documento");
            ComboBox<Contact> choice=new ComboBox<>();choice.setPromptText("Seleccionar "+kind.toLowerCase());choice.setMaxWidth(Double.MAX_VALUE);choice.setMinWidth(0);
            Runnable filter=()->{try{String q=search.getText().trim().toLowerCase();choice.getItems().setAll(data.contacts().stream().filter(c->kind.equals(c.kind())).filter(c->(c.given()+" "+c.family()+" "+c.number()).toLowerCase().contains(q)).toList());}catch(Exception ex){error(ex);}};
            search.textProperty().addListener((o,a,b)->filter.run());choice.setOnShowing(e->filter.run());filter.run();
            VBox row=new VBox(8,search,choice,button("Usar registro",()->{Contact selected=choice.getValue();if(selected==null)return;Map<String,String> values=ContactReuse.valuesFor(selected,group);for(var mapped:values.entrySet()){Control target=inputs.get(mapped.getKey());if(target!=null)setInput(target,mapped.getValue());else localValueOverrides.put(mapped.getKey(),mapped.getValue());}saved("Datos locales aplicados a "+entry.getKey()+"; puede editarlos antes de generar");}));
            generatedForm.getChildren().add(labelled("REGISTRO LOCAL · "+entry.getKey()+" · "+kind,row));
        }
    }
    private void makePreview(){
        generatedBytes=null;generatedPdf=null;Template selectedTemplate=generateTemplate.getValue();if(selectedTemplate==null)throw new IllegalArgumentException("Seleccione una plantilla.");
        try{
            Map<String,String> values=new LinkedHashMap<>();Map<String,Variable> vars=new HashMap<>();
            for(Variable v:variablesForTemplate(selectedTemplate)){vars.put(v.key(),v);values.put(v.key(),VariableInput.initialValue(v.type(),v.defaultValue()));}
            values.putAll(localValueOverrides);
            for(var entry:inputs.entrySet()){
                Control input=entry.getValue();Variable v=vars.get(entry.getKey());
                if(v==null)throw new IllegalArgumentException("Variable sin definir: "+entry.getKey());
                String value=VariableInput.validate(v.type(),readInput(input),Locale.getDefault());
                if(v.required()&&value.isBlank())throw new IllegalArgumentException("Complete el campo: "+v.label());values.put(entry.getKey(),value);
            }
            if(!values.equals(generatedValues))generatedHistoryId=0;
            Template t=store.templates().stream().filter(x->x.id()==selectedTemplate.id()).findFirst().orElseThrow();byte[] source=historicalTemplate==null?t.edited():historicalTemplate;
            try(XWPFDocument doc=DocumentEngine.open(source)){
                for(String marker:DocumentEngine.markers(doc)){
                    Variable v=vars.get(marker);if(v==null)throw new IllegalArgumentException("Variable sin definir: "+marker);
                    if(values.get(marker).isBlank()&&!inputs.containsKey(marker))throw new IllegalArgumentException("La variable '"+marker+"' no está en el formulario ni tiene valor predeterminado.");
                }
            }
            generatedBytes=DocumentEngine.generate(source,values);generatedValues=values;autoSaveContacts(selectedTemplate);preview.getChildren().clear();
            try(XWPFDocument result=DocumentEngine.open(generatedBytes)){
                for(IBodyElement element:result.getBodyElements()){
                    if(element instanceof XWPFParagraph p)preview.getChildren().add(renderParagraph(p,false));
                    else if(element instanceof XWPFTable table)for(XWPFTableRow row:table.getRows()){HBox cells=new HBox(8);for(XWPFTableCell cell:row.getTableCells())cells.getChildren().add(new Label(cell.getText()));preview.getChildren().add(cells);}
                }
            }
            saved("Vista previa generada"+contactSaveSummary);
        }catch(Exception e){error(e);}
    }
    private void saveGenerated(){makePreview();if(generatedBytes==null)return;FileChooser fc=new FileChooser();fc.setTitle("Guardar documento generado");fc.setInitialFileName(fileName()+".docx");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word DOCX","*.docx"));File target=fc.showSaveDialog(stage);if(target==null)return;try{Files.write(target.toPath(),generatedBytes);recordGeneration(target.toString(),null,null);saved("DOCX guardado: "+target.getName()+contactSaveSummary);}catch(Exception e){error(e);}}
    private void savePdf(){makePreview();if(generatedBytes==null)return;FileChooser fc=new FileChooser();fc.setTitle("Guardar PDF");fc.setInitialFileName(fileName()+".pdf");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));File target=fc.showSaveDialog(stage);if(target==null)return;try{generatedPdf=DocxPdfExporter.render(generatedBytes);Files.write(target.toPath(),generatedPdf);recordGeneration(null,target.toString(),generatedPdf);saved("PDF guardado: "+target.getName()+contactSaveSummary);}catch(Exception e){error(e);}}
    private void openGeneratedInWord(){
        makePreview();if(generatedBytes==null)return;
        try{Path document=Files.createTempFile("documento-",".docx");Files.write(document,generatedBytes);document.toFile().deleteOnExit();documentActions.open(document);saved("Documento abierto en el procesador de texto"+contactSaveSummary);}catch(Exception e){error(e);}
    }
    private void printGenerated(){
        makePreview();if(generatedBytes==null)return;
        try{generatedPdf=DocxPdfExporter.render(generatedBytes);Path document=Files.createTempFile("documento-",".pdf");Files.write(document,generatedPdf);document.toFile().deleteOnExit();documentActions.print(document);saved("Documento enviado a impresión"+contactSaveSummary);}catch(Exception e){error(e);}
    }
    private String fileName(){return generateTemplate.getValue().name().replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚ _-]","");}
    private void recordGeneration(String docxPath,String pdfPath,byte[] pdf)throws Exception{
        Template template=generateTemplate.getValue();int version=data.recordRevision(template.id(),historicalTemplate==null?template.edited():historicalTemplate);
        if(generatedHistoryId==0){History history=data.saveHistory(new History(0,template.name(),template.id(),version,null,json.writeValueAsString(generatedValues),docxPath,pdfPath,generatedBytes,pdf));generatedHistoryId=history.id();}
        else data.updateHistoryFiles(generatedHistoryId,docxPath,pdfPath,pdf);
        autoSaveContacts(template);
    }
    private void autoSaveContacts(Template template){contactSaveSummary="";try{
        List<Variable> variables=variablesForTemplate(template);
        Map<String,String> types=new HashMap<>();for(Variable v:variables){String service=queryService(template.id(),v);types.put(v.key(),service==null?v.type():service);}
        ContactReuse.Result result=ContactReuse.save(data,contactFormBindings(template.id(),variables),generatedValues,types);
        contactSaveSummary=" · Clientes: "+result.created()+" nuevos, "+result.updated()+" actualizados";refreshContacts();
    }catch(Exception e){contactSaveSummary=" · No se pudo actualizar Clientes: "+e.getMessage();}}
    private List<ContactBinding> contactFormBindings(long templateId,List<Variable> variables)throws Exception{
        Map<String,ApiBinding> api=new LinkedHashMap<>();for(Variable v:variables){long id=definitionId(v);ApiBinding binding=id>0?data.apiBinding(id):null;if(binding!=null)api.put(v.key(),binding);}
        return ContactReuse.formBindings(templateId,variables,data.contactBindings(templateId),data.apiContexts(templateId),api);
    }

    private void buildContacts(){
        contactsPage=new BorderPane();contactsPage.getStyleClass().add("page");contactSearch.setPromptText("Buscar nombre, documento o dirección");contactSearch.textProperty().addListener((o,a,b)->refreshContacts());contactList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->loadContact(b));
        twoLineCells(contactList,c->("Empresa".equals(c.kind())?c.given():c.given()+" "+c.family()),c->c.documentType()+" · "+c.number());
        VBox left=new VBox(8,contactSearch,contactList,button("Eliminar cliente",this::deleteContact));left.getStyleClass().add("side-panel");fixedWidth(left,320);uniformButtons(left);VBox.setVgrow(contactList,Priority.ALWAYS);contactsPage.setLeft(left);BorderPane.setMargin(left,new Insets(0,14,0,0));
        contactKind.getItems().setAll("Persona","Empresa");contactKind.setValue("Persona");contactKind.setOnAction(e->{boolean company="Empresa".equals(contactKind.getValue());contactFamily.setDisable(company);contactDocType.setDisable(company);if(company)contactDocType.setText("RUC");else if("RUC".equals(contactDocType.getText()))contactDocType.setText("DNI");});contactRepresentatives.setPrefRowCount(2);contactExtras.setPrefRowCount(3);contactRepresentatives.setWrapText(true);contactExtras.setWrapText(true);
        GridPane fields=twoColumns();fields.add(labelled("Tipo de cliente",contactKind),0,0);fields.add(labelled("Nombres / razón social",contactGiven),1,0);fields.add(labelled("Apellidos",contactFamily),0,1);fields.add(labelled("Tipo de documento",contactDocType),1,1);fields.add(labelled("Número de documento / RUC",contactNumber),0,2);fields.add(labelled("Domicilio / domicilio fiscal",contactAddress),1,2);fields.add(labelled("Representantes (empresa)",contactRepresentatives),0,3,2,1);fields.add(labelled("Datos adicionales · JSON",contactExtras),0,4,2,1);
        VBox form=new VBox(16,new Label("FICHA LOCAL"),fields);form.getStyleClass().add("catalog-form");BorderPane detail=new BorderPane(scrolling(form));HBox actions=new HBox(button("Guardar cliente",this::saveContact));actions.getStyleClass().add("action-bar");detail.setBottom(actions);detail.disableProperty().bind(contactList.getSelectionModel().selectedItemProperty().isNull());contactsPage.setCenter(detail);newContact();
    }
    private void showContacts(){refreshContacts();page("Clientes",contactsPage);}
    private void refreshContacts(){try{
        long selected=selectedContact==null?0:selectedContact.id();String q=contactSearch.getText().toLowerCase(Locale.ROOT);
        List<Contact> contacts=data.contacts().stream().filter(c->(c.given()+" "+c.family()+" "+c.number()+" "+c.address()).toLowerCase(Locale.ROOT).contains(q)).toList();
        contactList.getItems().setAll(contacts);contactList.getSelectionModel().select(contacts.stream().filter(c->c.id()==selected).findFirst().orElse(contacts.isEmpty()?null:contacts.get(0)));
        if(contacts.isEmpty())newContact();
    }catch(Exception e){error(e);}}
    private void newContact(){selectedContact=null;contactList.getSelectionModel().clearSelection();contactKind.setValue("Persona");contactGiven.clear();contactFamily.clear();contactDocType.setText("DNI");contactNumber.clear();contactAddress.clear();contactRepresentatives.clear();contactExtras.setText("{}");}
    private void loadContact(Contact c){if(c==null){newContact();return;}selectedContact=c;contactKind.setValue(c.kind());contactGiven.setText(c.given());contactFamily.setText(c.family());contactDocType.setText(c.documentType());contactNumber.setText(c.number());contactAddress.setText(c.address());contactRepresentatives.setText(c.representatives());contactExtras.setText(c.extrasJson());}
    private void saveContact(){if(selectedContact==null)throw new IllegalArgumentException("Seleccione un cliente para editarlo.");if(contactGiven.getText().isBlank())throw new IllegalArgumentException("Escriba nombre o razón social.");try{if(!json.readTree(contactExtras.getText()).isObject())throw new IllegalArgumentException("Los datos adicionales deben ser un objeto JSON.");Contact c=data.saveContact(new Contact(selectedContact.id(),contactKind.getValue(),contactGiven.getText().trim(),contactFamily.getText().trim(),"Empresa".equals(contactKind.getValue())?"RUC":contactDocType.getText().trim(),contactNumber.getText().trim(),contactAddress.getText().trim(),contactRepresentatives.getText().trim(),contactExtras.getText()));refreshContacts();contactList.getSelectionModel().select(c);saved("Registro local guardado");}catch(Exception e){error(e);}}
    private void deleteContact(){Contact c=contactList.getSelectionModel().getSelectedItem();if(c==null)return;try{data.deleteContact(c.id());newContact();refreshContacts();saved("Registro eliminado");}catch(Exception e){error(e);}}

    private void buildApi(){
        apiPage=new BorderPane();apiPage.getStyleClass().add("page");apiToken.setPromptText("Clave nueva o de reemplazo");apiType.getItems().setAll("DNI","RUC");apiType.setValue("DNI");apiNumber.setPromptText("Número a consultar");credentialProvider.getItems().setAll("Factiliza","APIsPERU");credentialProvider.setValue("Factiliza");credentialName.setPromptText("Nombre de la credencial");credentialList.setPrefHeight(150);credentialList.setMinHeight(110);twoLineCells(credentialList,ApiCredentialStore.Credential::name,c->c.provider()+" · "+c.status());credentialList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->loadCredential(b));credentialStats.setWrapText(true);
        VBox manager=new VBox(10,new Label("CREDENCIALES LOCALES"),credentialList,button("+ Nueva credencial",this::newCredential),labelled("Nombre",credentialName),labelled("Proveedor",credentialProvider),labelled("Clave API",apiToken),credentialEnabled,button("Guardar credencial",this::saveCredential),button("Eliminar credencial",this::deleteCredential),credentialStats);manager.getStyleClass().add("side-panel");uniformButtons(manager);ScrollPane managerScroll=scrolling(manager);fixedWidth(managerScroll,380);apiPage.setLeft(managerScroll);BorderPane.setMargin(managerScroll,new Insets(0,14,0,0));
        GridPane query=twoColumns();query.add(labelled("Documento",apiType),0,0);query.add(labelled("Número",apiNumber),1,0);
        FlowPane actions=new FlowPane(8,6,button("Consultar",()->runApiQuery(apiType.getValue(),apiNumber.getText(),null,this::showApiResult)),button("Probar seleccionada",()->{ApiCredentialStore.Credential c=credentialList.getSelectionModel().getSelectedItem();if(c==null)throw new IllegalArgumentException("Seleccione una credencial.");runApiQuery(apiType.getValue(),apiNumber.getText(),c.id(),this::showApiResult);}));
        apiState.setWrapText(true);apiSaved.setWrapText(true);apiSaved.getStyleClass().add("automatic-save");VBox content=new VBox(14,new Label("CONSULTA DNI / RUC"),query,actions,apiState,apiSaved,apiResultBox);content.getStyleClass().add("catalog-form");apiPage.setCenter(scrolling(content));refreshCredentialList();newCredential();
    }
    private void showApi(){refreshCredentialList();page("Conexiones API",apiPage);apiState.setText(credentialList.getItems().isEmpty()?"Sin credenciales · El sistema funciona sin conexión":"Consultas opcionales · Selección automática de proveedor");}
    private void refreshCredentialList(){try{long id=credentialList.getSelectionModel().getSelectedItem()==null?0:credentialList.getSelectionModel().getSelectedItem().id();credentialList.getItems().setAll(credentials.credentials());if(id>0)credentialList.getSelectionModel().select(credentialList.getItems().stream().filter(c->c.id()==id).findFirst().orElse(null));}catch(Exception e){error(e);}}
    private void newCredential(){credentialList.getSelectionModel().clearSelection();credentialName.clear();credentialProvider.setDisable(false);credentialProvider.setValue("Factiliza");apiToken.clear();credentialEnabled.setSelected(true);credentialStats.setText("Sin credencial seleccionada");}
    private void loadCredential(ApiCredentialStore.Credential c){if(c==null)return;credentialName.setText(c.name());credentialProvider.setValue(c.provider());credentialProvider.setDisable(true);apiToken.clear();credentialEnabled.setSelected(c.enabled());credentialStats.setText("Registrada: "+c.createdAt()+"\nMes actual: "+c.requests()+" solicitudes · "+c.successes()+" correctas · "+c.failures()+" fallidas\nÚltimo uso: "+(c.lastUsed()==null?"ninguno":c.lastUsed())+"\nEstado: "+c.status());}
    private void saveCredential(){try{ApiCredentialStore.Credential current=credentialList.getSelectionModel().getSelectedItem();if(current==null){ApiCredentialStore.Credential added=credentials.add(credentialName.getText(),credentialProvider.getValue(),apiToken.getText());credentials.enabled(added.id(),credentialEnabled.isSelected());refreshCredentialList();credentialList.getSelectionModel().select(credentialList.getItems().stream().filter(c->c.id()==added.id()).findFirst().orElse(null));}else{credentials.rename(current.id(),credentialName.getText());credentials.enabled(current.id(),credentialEnabled.isSelected());if(!apiToken.getText().isBlank())credentials.replace(current.id(),apiToken.getText());refreshCredentialList();}apiToken.clear();saved("Credencial guardada localmente");}catch(Exception e){error(e);}}
    private void deleteCredential(){ApiCredentialStore.Credential c=credentialList.getSelectionModel().getSelectedItem();if(c==null)return;if(new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la credencial '"+c.name()+"'? Los documentos y datos locales permanecerán intactos.",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;try{credentials.delete(c.id());refreshCredentialList();newCredential();saved("Credencial eliminada; evidencia no sensible conservada");}catch(Exception e){error(e);}}
    private void runApiQuery(String type,String number,Long credentialId,java.util.function.Consumer<LookupRouter.Result> onSuccess){
        if(apiQueryRunning){saved("Ya hay una consulta en curso.");return;}apiQueryRunning=true;apiSaved.setText("");apiState.setText("Consultando…");
        Task<LookupRouter.Result> task=new Task<>(){@Override protected LookupRouter.Result call()throws Exception{
            LookupRouter.Result result=credentialId==null?lookupRouter.query(type,number.trim()):lookupRouter.querySpecific(type,number.trim(),credentialId);
            if(result.usable())data.upsertContact(ContactReuse.fromApi(result));return result;
        }};
        task.setOnSucceeded(e->{apiQueryRunning=false;LookupRouter.Result result=task.getValue();apiState.setText(result.usable()?"Respondió "+result.provider()+" · "+(result.status()==LookupRouter.Status.PARTIAL?"datos parciales":"consulta correcta"):result.error());refreshCredentialList();if(result.usable()){apiSaved.setText("✓ Guardado automáticamente en Clientes");refreshContacts();onSuccess.accept(result);}});
        task.setOnFailed(e->{apiQueryRunning=false;apiState.setText("Consulta no completada: "+task.getException().getMessage());refreshCredentialList();});Thread thread=new Thread(task,"consulta-documental");thread.setDaemon(true);thread.start();
    }
    private void showApiResult(LookupRouter.Result result){
        apiResultBox.getChildren().clear();GridPane grid=twoColumns();int index=0;
        for(ApiFieldCatalog.Field item:ApiFieldCatalog.fields(result.service())){
            String value=result.values().getOrDefault(item.key(),"");Label dataLabel=new Label(value.isBlank()?"No disponible":value);dataLabel.setWrapText(true);dataLabel.setMinWidth(0);VBox cell=labelled(item.label(),dataLabel);cell.getStyleClass().add("result-field");grid.add(cell,index%2,index/2);index++;
        }
        apiResultBox.getChildren().addAll(description("Proveedor: "+result.provider()),grid);apiSaved.setText("✓ Guardado automáticamente en Clientes");saved("Consulta completada · Cliente guardado automáticamente");
    }

    private void refreshMappings(){Template t=mapTemplate.getValue();try{if(t==null){mapSource.getItems().clear();mapTarget.getItems().clear();mappingList.getItems().clear();return;}List<Variable> vars=store.variables(t.id());mapSource.getItems().setAll(vars.stream().filter(v->v.type().equals("DNI")||v.type().equals("RUC")).toList());mapTarget.getItems().setAll(vars);mappingList.getItems().setAll(data.mappings(t.id()));}catch(Exception e){error(e);}}
    private void refreshApiFieldChoices(){Variable source=mapSource.getValue();if(source==null)return;if(source.type().equals("DNI"))mapApiField.getItems().setAll("numero","nombres","apellido_paterno","apellido_materno","nombre_completo","direccion","direccion_completa","departamento","provincia","distrito");else mapApiField.getItems().setAll("numero","nombre_o_razon_social","direccion","direccion_completa","tipo_contribuyente","estado","condicion","departamento","provincia","distrito");}
    private void saveMapping(){Template t=mapTemplate.getValue();Variable source=mapSource.getValue(),target=mapTarget.getValue();String field=mapApiField.getValue();if(t==null||source==null||target==null||field==null||field.isBlank())throw new IllegalArgumentException("Seleccione plantilla, origen, dato y destino.");try{data.saveMapping(new Mapping(0,t.id(),source.key(),field.trim(),target.key()));refreshMappings();saved("Correspondencia guardada");}catch(Exception e){error(e);}}
    private void deleteMapping(){Mapping m=mappingList.getSelectionModel().getSelectedItem();if(m==null)return;try{data.deleteMapping(m.id());refreshMappings();saved("Correspondencia eliminada");}catch(Exception e){error(e);}}
    private long definitionId(Variable variable)throws Exception{return variable.id()<0?-variable.id():store.definitionId(variable.id());}
    private String queryService(long templateId,Variable variable)throws Exception{
        for(ApiContext context:data.apiContexts(templateId))if(context.variableKey().equals(variable.key()))return context.source()?context.service():null;
        for(ContactBinding binding:data.contactBindings(templateId))if(binding.variableKey().equals(variable.key()))return "number".equals(binding.field())?("Empresa".equals(binding.kind())?"RUC":"DNI"):null;
        if(List.of("DNI","RUC").contains(variable.type()))return variable.type();
        long id=definitionId(variable);ApiBinding binding=id>0?data.apiBinding(id):null;
        if(binding!=null)return "numero".equals(ApiFieldCatalog.canonical(binding.service(),binding.path()))?binding.service():null;
        return ContactReuse.identifierService(variable);
    }
    private void lookupForField(Template template,Variable variable,TextField field,String service){
        PauseTransition delay=lookupDelays.get(variable.key());if(delay!=null)delay.stop();
        String number=field.getText().trim();if(!validQueryNumber(service,number))throw new IllegalArgumentException("Número de documento inválido.");
        try{if(loadLocalForField(template,variable,field,service))return;}catch(Exception e){error(e);return;}
        runApiQuery(service,number,null,result->{
            if(generateTemplate.getValue()==null||generateTemplate.getValue().id()!=template.id()||inputs.get(variable.key())!=field||!field.getText().trim().equals(number))return;
            int count=applyApiBindings(template.id(),variable.key(),result);
            try{count+=applyContactFields(template.id(),variable.key(),data.matchingContact("RUC".equals(service)?"Empresa":"Persona",service,number),service);}catch(Exception e){error(e);}
            saved("Respondió "+result.provider()+" · "+count+" campos completados · Cliente guardado automáticamente");
        });
    }
    private static boolean validQueryNumber(String service,String number){return number!=null&&number.trim().matches("RUC".equals(service)?"\\d{11}":"\\d{8}");}
    private boolean loadLocalForField(Template template,Variable variable,TextField field,String service)throws Exception{
        Contact local=data.matchingContact("RUC".equals(service)?"Empresa":"Persona",service,field.getText());if(local==null)return false;
        resetContactEdits(template,variable,service);
        Map<String,String> values=ContactReuse.apiValues(local);LookupRouter.Result result=new LookupRouter.Result(service,field.getText().trim(),"Clientes locales",values,values.keySet(),Set.of(),LookupRouter.Status.SUCCESS,"",values);
        applyApiBindings(template.id(),variable.key(),result);applyContactFields(template.id(),variable.key(),local,service);saved("Datos cargados desde Clientes locales");return true;
    }
    private void resetContactEdits(Template template,Variable source,String service)throws Exception{
        List<ContactBinding> bindings=contactFormBindings(template.id(),new ArrayList<>(formVariables.values()));String group=contextGroup(data.apiContexts(template.id()),bindings,source.key(),service);String kind="RUC".equals(service)?"Empresa":"Persona";
        long sources=bindings.stream().filter(b->"number".equals(b.field())&&b.kind().equals(kind)).count();
        for(ContactBinding binding:bindings)if(!binding.variableKey().equals(source.key())&&binding.kind().equals(kind)&&sameQueryGroup(group,binding.groupKey(),sources,source.key(),binding.variableKey()))manuallyEdited.remove(binding.variableKey());
    }
    private int applyContactFields(long templateId,String sourceKey,Contact contact,String service)throws Exception{
        if(contact==null)return 0;List<ContactBinding> bindings=contactFormBindings(templateId,new ArrayList<>(formVariables.values()));List<ApiContext> contexts=data.apiContexts(templateId);
        String sourceGroup=contextGroup(contexts,bindings,sourceKey,service);long count=bindings.stream().filter(b->"number".equals(b.field())&&b.kind().equals(contact.kind())).count();int completed=0;
        for(ContactBinding binding:bindings){if(!binding.kind().equals(contact.kind())||!sameQueryGroup(sourceGroup,binding.groupKey(),count,sourceKey,binding.variableKey())||manuallyEdited.contains(binding.variableKey()))continue;
            String value=ContactReuse.valuesFor(contact,List.of(binding)).get(binding.variableKey());Control target=inputs.get(binding.variableKey());if(target!=null&&value!=null&&!value.isBlank()){applyingApi=true;try{setInput(target,value);completed++;}finally{applyingApi=false;}}
        }return completed;
    }
    private int applyApiBindings(long templateId,String sourceKey,LookupRouter.Result result){int completed=0;try{
        List<ContactBinding> contacts=data.contactBindings(templateId);List<ApiContext> contexts=data.apiContexts(templateId);String sourceGroup=contextGroup(contexts,contacts,sourceKey,result.service());
        long sources=formVariables.values().stream().filter(v->{try{return result.service().equals(queryService(templateId,v));}catch(Exception e){return false;}}).count();
        for(var entry:formVariables.entrySet()){
            Variable variable=entry.getValue();long id=definitionId(variable);ApiBinding binding=id>0?data.apiBinding(id):null;
            if(binding==null||!binding.service().equals(result.service()))continue;
            String targetGroup=contextGroup(contexts,contacts,variable.key(),result.service());
            if(!sameQueryGroup(sourceGroup,targetGroup,sources,sourceKey,variable.key()))continue;
            String normalized=ApiFieldCatalog.canonical(binding.service(),binding.path());String value=normalized.startsWith("legacy:")?("Clientes locales".equals(result.provider())?result.raw().get(binding.path()):ApiFieldCatalog.legacyValue(result.provider(),binding.path(),result.raw())):result.values().get(normalized);
            if(value!=null&&!value.isBlank()&&!manuallyEdited.contains(variable.key())){Control target=inputs.get(variable.key());if(target!=null){applyingApi=true;try{setInput(target,value);completed++;}finally{applyingApi=false;}}}
        }
        for(Mapping mapping:data.mappings(templateId))if(mapping.sourceKey().equals(sourceKey)&&!manuallyEdited.contains(mapping.targetKey())&&sameQueryGroup(sourceGroup,contextGroup(contexts,contacts,mapping.targetKey(),result.service()),sources,sourceKey,mapping.targetKey())){String value=result.raw().getOrDefault(mapping.apiField(),result.raw().getOrDefault("data."+mapping.apiField(),result.values().get(ApiFieldCatalog.canonical(result.service(),mapping.apiField()))));Control target=inputs.get(mapping.targetKey());if(value!=null&&!value.isBlank()&&target!=null){applyingApi=true;try{setInput(target,value);completed++;}finally{applyingApi=false;}}}
    }catch(Exception e){error(e);}return completed;}
    static String contextGroup(List<ApiContext> contexts,List<ContactBinding> contacts,String key,String service){for(ApiContext c:contexts)if(c.variableKey().equals(key)&&c.service().equals(service))return c.groupKey();for(ContactBinding b:contacts)if(b.variableKey().equals(key))return b.groupKey();return null;}
    static boolean sameQueryGroup(String source,String target,long sourceCount,String sourceKey,String targetKey){if(source!=null)return source.equals(target)||sourceKey.equals(targetKey);return sourceCount<=1||sourceKey.equals(targetKey);}
    private static String readInput(Control input){if(input instanceof DatePicker picker)return picker.getValue()==null?"":picker.getValue().toString();if(input instanceof TextArea area)return area.getText();return ((TextField)input).getText();}
    private static void setInput(Control input,String value){if(input instanceof DatePicker picker){try{picker.setValue(LocalDate.parse(value));}catch(Exception first){try{picker.setValue(LocalDate.parse(value,java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")));}catch(Exception ignored){}}}else if(input instanceof TextArea area)area.setText(value);else ((TextField)input).setText(value);}

    private void buildHistory(){
        historyPage=new BorderPane();historyPage.getStyleClass().add("page");historySearch.setPromptText("Buscar título o fecha");historySearch.setMinWidth(0);historySearch.textProperty().addListener((o,a,b)->refreshHistory());historyDate.setPromptText("Fecha");historyDate.setMinWidth(0);historyDate.setPrefWidth(120);historyDate.valueProperty().addListener((o,a,b)->refreshHistory());
        Button clear=button("×",()->{historySearch.clear();historyDate.setValue(null);});clear.setTooltip(new Tooltip("Quitar filtros"));HBox filter=new HBox(6,historySearch,historyDate,clear);HBox.setHgrow(historySearch,Priority.ALWAYS);
        twoLineCells(historyList,History::title,h->h.generatedAt()+" · Versión "+h.templateVersion());VBox list=new VBox(10,filter,historyList);list.getStyleClass().add("side-panel");fixedWidth(list,380);VBox.setVgrow(historyList,Priority.ALWAYS);historyPage.setLeft(list);BorderPane.setMargin(list,new Insets(0,14,0,0));
        Label summary=description("Seleccione un registro para ver sus detalles y recuperar sus archivos.");historyList.getSelectionModel().selectedItemProperty().addListener((o,a,h)->summary.setText(h==null?"Seleccione un registro.":h.title()+"\nGenerado: "+h.generatedAt()+"\nVersión de plantilla: "+h.templateVersion()+"\nDOCX: "+(h.docxPath()==null?"Conservado en el historial":h.docxPath())+"\nPDF: "+(h.pdfPath()==null?"Disponible para exportar":h.pdfPath())));
        GridPane actions=twoColumns();List<Button> buttons=List.of(button("Abrir DOCX",this::openHistory),button("Duplicar registro",this::duplicateHistory),button("Editar y regenerar",this::editHistory),button("Exportar DOCX",()->exportHistory(false)),button("Exportar PDF",()->exportHistory(true)),button("Eliminar registro",this::deleteHistory));int i=0;for(Button b:buttons){b.setWrapText(true);b.setMaxWidth(Double.MAX_VALUE);actions.add(b,i%2,i/2);i++;}
        VBox detail=new VBox(18,new Label("DOCUMENTO SELECCIONADO"),summary,actions);detail.getStyleClass().add("catalog-form");historyPage.setCenter(scrolling(detail));
    }
    private void showHistory(){refreshHistory();page("Historial",historyPage);}
    private void refreshHistory(){try{String q=historySearch.getText().toLowerCase();String date=historyDate.getValue()==null?null:historyDate.getValue().toString();historyList.getItems().setAll(data.history().stream().filter(h->(h.title()+" "+h.generatedAt()).toLowerCase().contains(q)).filter(h->date==null||h.generatedAt().startsWith(date)).toList());}catch(Exception e){error(e);}}
    private History selectedHistory(){History h=historyList.getSelectionModel().getSelectedItem();if(h==null)throw new IllegalArgumentException("Seleccione un documento del historial.");return h;}
    private void openHistory(){try{History h=selectedHistory();Path dir=store.directory().resolve("history-files");Files.createDirectories(dir);Path path=dir.resolve("documento-"+h.id()+".docx");Files.write(path,h.docx());if(!Desktop.isDesktopSupported())throw new IllegalStateException("No hay aplicación asociada a DOCX en este equipo.");Desktop.getDesktop().open(path.toFile());saved("Abriendo DOCX conservado en el historial");}catch(Exception e){error(e);}}
    private void duplicateHistory(){try{History h=selectedHistory();data.saveHistory(new History(0,h.title()+" (copia)",h.templateId(),h.templateVersion(),null,h.valuesJson(),null,null,h.docx(),h.pdf()));refreshHistory();saved("Registro duplicado");}catch(Exception e){error(e);}}
    private void editHistory(){try{
        History history=selectedHistory();Template template=store.templates().stream().filter(t->t.id()==history.templateId()).findFirst().orElseThrow(()->new IllegalStateException("La plantilla original ya no existe."));
        showGenerator();TemplateGroup group=null;for(TemplateGroup candidate:generateGroup.getItems())if(store.inGroup(template.id(),candidate.id())){group=candidate;break;}
        if(group==null)throw new IllegalStateException("Asocie la plantilla a un grupo para generar documentos.");
        generateSearch.clear();generateGroup.getSelectionModel().select(group);refreshKinds();generateTemplate.getSelectionModel().select(generateTemplate.getItems().stream().filter(t->t.id()==template.id()).findFirst().orElseThrow());buildDynamicForm();
        historicalTemplate=data.revision(history.templateId(),history.templateVersion());if(historicalTemplate==null)throw new IllegalStateException("No se conserva la versión de la plantilla.");
        var values=json.readTree(history.valuesJson());applyingApi=true;try{for(var entry:inputs.entrySet())setInput(entry.getValue(),values.path(entry.getKey()).asText(""));}finally{applyingApi=false;}
        saved("Valores recuperados; edite y genere un nuevo documento");
    }catch(Exception e){error(e);}}

    private void exportHistory(boolean pdf){try{History h=selectedHistory();byte[] content=pdf?(h.pdf()==null?DocxPdfExporter.render(h.docx()):h.pdf()):h.docx();FileChooser fc=new FileChooser();fc.setInitialFileName(h.title().replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚ _-]","")+(pdf?".pdf":".docx"));fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(pdf?"PDF":"Word DOCX",pdf?"*.pdf":"*.docx"));File file=fc.showSaveDialog(stage);if(file==null)return;Files.write(file.toPath(),content);data.updateHistoryFiles(h.id(),pdf?null:file.toString(),pdf?file.toString():null,pdf?content:null);refreshHistory();saved("Archivo exportado nuevamente");}catch(Exception e){error(e);}}
    private void deleteHistory(){try{History h=selectedHistory();data.deleteHistory(h.id());refreshHistory();saved("Registro eliminado; los archivos exportados permanecen en su ubicación");}catch(Exception e){error(e);}}

    private void buildSettings(){
        settingsPage=new BorderPane();settingsPage.getStyleClass().add("page");
        importConflict.getItems().setAll("Conservar existentes","Reemplazar existentes");importConflict.getSelectionModel().selectFirst();
        GridPane importRow=twoColumns();Button importButton=button("Importar archivo (.zip)",this::importConfig);GridPane.setValignment(importButton,VPos.BOTTOM);importRow.add(importButton,0,0);importRow.add(labelled("Resolución de duplicados",importConflict),1,0);
        VBox importing=new VBox(12,new Label("Importar"),description("El archivo puede contener plantillas, clientes o ambos."),importRow);importing.getStyleClass().add("transfer-card");
        exportScope.getItems().setAll("Solo una plantilla","Todas las plantillas de un grupo","Todas las plantillas");exportScope.getSelectionModel().selectFirst();
        exportTemplate.setPromptText("Seleccione una plantilla");exportGroup.setPromptText("Seleccione un grupo");
        exportTemplate.disableProperty().bind(exportScope.valueProperty().isNotEqualTo("Solo una plantilla"));exportGroup.disableProperty().bind(exportScope.valueProperty().isNotEqualTo("Todas las plantillas de un grupo"));
        GridPane scopeFields=twoColumns();scopeFields.add(labelled("Plantilla",exportTemplate),0,0);scopeFields.add(labelled("Grupo",exportGroup),1,0);
        VBox templates=new VBox(12,new Label("Exportar Plantillas"),description("Incluye los documentos y sus variables, grupos y asociaciones. Las credenciales permanecen en este equipo."),labelled("Alcance de exportación",exportScope),scopeFields,button("Exportar plantillas (.zip)",this::exportConfig));templates.getStyleClass().add("transfer-card");
        VBox clients=new VBox(12,new Label("Exportar Clientes"),description("Exporta todas las personas y empresas, incluidos sus datos adicionales."),button("Exportar clientes (.zip)",this::exportClients));clients.getStyleClass().add("transfer-card");
        VBox content=new VBox(14,importing,templates,clients);content.setFillWidth(true);settingsPage.setCenter(scrolling(content));
    }
    private void showSettings(){try{
        Template selected=exportTemplate.getValue();TemplateGroup group=exportGroup.getValue();exportTemplate.getItems().setAll(store.templates());exportGroup.getItems().setAll(store.groups());
        exportTemplate.getSelectionModel().select(exportTemplate.getItems().stream().filter(t->selected!=null&&t.id()==selected.id()).findFirst().orElse(null));
        exportGroup.getSelectionModel().select(exportGroup.getItems().stream().filter(g->group!=null&&g.id()==group.id()).findFirst().orElse(null));
        if(exportTemplate.getValue()==null)exportTemplate.getSelectionModel().selectFirst();if(exportGroup.getValue()==null)exportGroup.getSelectionModel().selectFirst();
    }catch(Exception e){error(e);}page("Configuración y respaldos",settingsPage);}
    private void exportConfig(){try{
        Collection<Long> ids=null;
        if("Solo una plantilla".equals(exportScope.getValue())){if(exportTemplate.getValue()==null)throw new IllegalArgumentException("Seleccione una plantilla.");ids=List.of(exportTemplate.getValue().id());}
        else if("Todas las plantillas de un grupo".equals(exportScope.getValue())){if(exportGroup.getValue()==null)throw new IllegalArgumentException("Seleccione un grupo.");List<Long> selected=new ArrayList<>();for(Template t:store.templates())if(store.inGroup(t.id(),exportGroup.getValue().id()))selected.add(t.id());if(selected.isEmpty())throw new IllegalArgumentException("El grupo no contiene plantillas.");ids=selected;}
        File file=zipChooser("plantillas-documentales.zip").showSaveDialog(stage);if(file==null)return;transfer.exportConfiguration(file.toPath(),ids);saved("Plantillas exportadas con todos sus datos relacionados");
    }catch(Exception e){error(e);}}
    private void importConfig(){File file=zipChooser(null).showOpenDialog(stage);if(file==null)return;try{
        StudioTransfer.ImportResult result=transfer.importConfiguration(file.toPath(),"Reemplazar existentes".equals(importConflict.getValue())?StudioTransfer.Conflict.REPLACE:StudioTransfer.Conflict.KEEP);
        refreshTemplates();refreshLibrary();refreshKinds();refreshDefinitions();refreshContacts();showSettings();
        saved("Importadas "+result.templates()+" plantillas, "+result.kinds()+" tipos y "+result.clients()+" clientes · Coincidencias: "+result.conflicts());
    }catch(Exception e){error(e);}}
    private static FileChooser zipChooser(String name){FileChooser chooser=new FileChooser();chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo ZIP","*.zip"));if(name!=null)chooser.setInitialFileName(name);return chooser;}
    private void exportClients(){File file=zipChooser("clientes.zip").showSaveDialog(stage);if(file==null)return;try{transfer.exportClients(file.toPath());saved("Clientes exportados con sus datos adicionales");}catch(Exception e){error(e);}}
}
