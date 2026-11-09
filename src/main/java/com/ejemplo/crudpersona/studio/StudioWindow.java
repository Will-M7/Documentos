package com.ejemplo.crudpersona.studio;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.util.Animations;
import com.ejemplo.crudpersona.studio.DocumentStore.Kind;
import com.ejemplo.crudpersona.studio.DocumentStore.Template;
import com.ejemplo.crudpersona.studio.DocumentStore.Variable;
import com.ejemplo.crudpersona.studio.DocumentStore.Definition;
import com.ejemplo.crudpersona.studio.StudioData.Contact;
import com.ejemplo.crudpersona.studio.StudioData.History;
import com.ejemplo.crudpersona.studio.StudioData.Mapping;
import com.ejemplo.crudpersona.studio.StudioData.ContactBinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
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

/** Offline desktop workflow: template editor, document catalog and generator. */
public final class StudioWindow {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*)}}");
    private static final List<String> TYPES = List.of("Texto corto", "Texto largo", "Número", "Fecha", "DNI", "RUC", "Monto", "Domicilio");
    private final Stage stage;
    private final DocumentStore store;
    private final StudioData data;
    private final LocalSecret secret;
    private final FactilizaClient factiliza=new FactilizaClient();
    private final StudioTransfer transfer;
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
    private final ListView<Definition> variableList = new ListView<>();
    private final VBox paper = new VBox(10);
    private final ScrollPane documentScroll = new ScrollPane();
    private final Label pageIndicator = new Label("Página 1");
    private final ComboBox<String> zoomChoice = new ComboBox<>();
    private final TextField paletteSearch = new TextField();
    private VBox palettePanel;
    private Button paletteToggle;
    private TabPane editorTabs;
    private final Scale paperScale = new Scale(1,1);
    private double pageWidth = 794, pageHeight = 1123;
    private String zoomMode = "Ancho";
    private boolean readingPageSize;
    private final TextField templateName = new TextField();
    private BorderPane definitionsPage;
    private final ListView<Definition> definitionList=new ListView<>();
    private final TextField definitionSearch=new TextField(),definitionKey=new TextField(),definitionLabel=new TextField(),definitionDefault=new TextField();
    private final ComboBox<String> definitionType=new ComboBox<>();
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

    private final ComboBox<Kind> generateKind = new ComboBox<>();
    private final VBox generatedForm = new VBox(15);
    private final Map<String, Control> inputs = new LinkedHashMap<>();
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
    private final ComboBox<String> apiType=new ComboBox<>();
    private final TextField apiNumber=new TextField();
    private final Label apiState=new Label("Sin consulta");
    private final VBox apiResultBox=new VBox(8);
    private final Map<String,TextField> apiResultFields=new LinkedHashMap<>();
    private String lastApiType;
    private final ComboBox<Template> mapTemplate=new ComboBox<>();
    private final ComboBox<Variable> mapSource=new ComboBox<>(),mapTarget=new ComboBox<>();
    private final ComboBox<String> mapApiField=new ComboBox<>();
    private final ListView<Mapping> mappingList=new ListView<>();
    private final ListView<History> historyList=new ListView<>();
    private final TextField historySearch=new TextField();
    private final DatePicker historyDate=new DatePicker();

    public StudioWindow(Stage stage, DocumentStore store) throws Exception {
        this.stage=stage; this.store=store;
        data=new StudioData(store.directory());secret=new LocalSecret(store.directory());transfer=new StudioTransfer(store,data);
        buildShell();
        buildDefinitions();
        buildEditor();
        buildCatalog();
        attachGenerationConfiguration();
        buildGenerator();
        buildContacts();buildHistory();buildApi();buildSettings();
        menu.selectToggle(generateNavigation);
        Platform.runLater(() -> moveIndicator(generateNavigation));
    }
    public Parent root() { return root; }

    private void buildShell() {
        root.getStyleClass().add("app");
        sidebar=new VBox(6);sidebar.getStyleClass().add("sidebar");sidebar.setPrefWidth(248);sidebar.setMinWidth(248);
        StackPane logo=new StackPane(new FontIcon("fth-layers"));logo.getStyleClass().add("marca-logo");
        brandTexts=new VBox(1,new Label("Gestion"),new Label("Panel administrativo"));
        brandTexts.getChildren().get(0).getStyleClass().add("marca-titulo");brandTexts.getChildren().get(1).getStyleClass().add("marca-subtitulo");
        HBox brand=new HBox(12,logo,brandTexts);brand.getStyleClass().add("marca");sidebar.getChildren().add(brand);
        indicator=new Region();indicator.getStyleClass().add("nav-indicador");indicator.setPrefHeight(42);indicator.setMaxHeight(42);
        VBox items=new VBox(3);
        addSection(items,"GENERAL");generateNavigation=addNavigation(items,"Generar documentos","fth-file-text",this::showGenerator);
        addSection(items,"DOCUMENTOS");addNavigation(items,"Variables","fth-tag",this::showDefinitions);addNavigation(items,"Editor de plantillas","fth-edit-3",this::showEditor);
        addSection(items,"REGISTROS");addNavigation(items,"Personas y empresas","fth-users",this::showContacts);addNavigation(items,"Historial","fth-clock",this::showHistory);
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
    private void collapseMenu(){collapsed=!collapsed;double width=collapsed?76:248;Timeline transition=new Timeline(new KeyFrame(Duration.millis(260),new KeyValue(sidebar.prefWidthProperty(),width,Interpolator.SPLINE(0.4,0,0.2,1)),new KeyValue(sidebar.minWidthProperty(),width,Interpolator.SPLINE(0.4,0,0.2,1))));List<Node> texts=new ArrayList<>(menuTexts);texts.add(brandTexts);List<ButtonBase> buttons=new ArrayList<>(navigation);buttons.add(themeButton);buttons.add(collapseButton);if(collapsed){texts.forEach(n->{n.setVisible(false);n.setManaged(false);});buttons.forEach(b->b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY));transition.play();}else{transition.setOnFinished(e->{texts.forEach(n->{n.setVisible(true);n.setManaged(true);});buttons.forEach(b->b.setContentDisplay(ContentDisplay.LEFT));ParallelTransition fade=new ParallelTransition();texts.forEach(n->fade.getChildren().add(Animations.fadeIn(n,Duration.millis(200))));fade.play();Platform.runLater(()->moveIndicator(menu.getSelectedToggle()));});transition.play();}((FontIcon)collapseButton.getGraphic()).setIconCode(collapsed?Feather.CHEVRONS_RIGHT:Feather.CHEVRONS_LEFT);Platform.runLater(()->moveIndicator(menu.getSelectedToggle()));}
    private void page(String title,Node content){Label heading=new Label(title);heading.getStyleClass().add("pagina-titulo");status.getStyleClass().add("pagina-subtitulo");status.setText("Datos locales · "+store.directory());VBox headerText=new VBox(4,heading,status);HBox header=new HBox(headerText);header.getStyleClass().add("pagina-cabecera");VBox page=new VBox(header,content);page.getStyleClass().add("pagina");VBox.setVgrow(content,Priority.ALWAYS);workspace.getChildren().setAll(page);Animations.fadeInUp(page,Duration.millis(380)).play();}
    private Button button(String label,Runnable action){
        Button b=new Button(label);b.getStyleClass().add("button-outlined");
        if(label.startsWith("Guardar")||label.startsWith("Crear respaldo"))b.getStyleClass().setAll("accent","boton-principal");
        else if(label.startsWith("Eliminar"))b.getStyleClass().add("danger");
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
    private static VBox labelled(String name,Node node){Label l=new Label(name);l.getStyleClass().add("field-label");VBox box=new VBox(5,l,node);return box;}

    private void buildDefinitions(){
        definitionsPage=new BorderPane();definitionsPage.getStyleClass().add("page");
        definitionSearch.setPromptText("Buscar identificador, etiqueta o tipo");definitionSearch.getStyleClass().add("buscador");
        definitionSearch.textProperty().addListener((o,a,b)->refreshDefinitions());
        definitionList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->loadDefinition(b));
        VBox left=new VBox(8,definitionSearch,definitionList,button("+ Nueva variable",this::newDefinition),button("Duplicar variable",this::duplicateDefinition),button("Eliminar variable",this::deleteDefinition));
        left.getStyleClass().add("side-panel");left.setPrefWidth(310);VBox.setVgrow(definitionList,Priority.ALWAYS);definitionsPage.setLeft(left);
        definitionType.getItems().setAll(TYPES);
        VBox form=new VBox(12,new Label("DEFINICIÓN DE VARIABLE"),labelled("Identificador estable",definitionKey),labelled("Etiqueta visible",definitionLabel),labelled("Tipo de entrada",definitionType),labelled("Valor predeterminado opcional",definitionDefault),button("Guardar variable",this::saveDefinition));
        form.getStyleClass().add("catalog-form");ScrollPane scroll=new ScrollPane(form);scroll.setFitToWidth(true);definitionsPage.setCenter(scroll);
        newDefinition();
    }
    private void showDefinitions(){refreshDefinitions();page("Variables",definitionsPage);}
    private void refreshDefinitions(){try{long selected=selectedDefinition==null?0:selectedDefinition.id();String q=definitionSearch.getText().trim().toLowerCase();definitionList.getItems().setAll(store.definitions().stream().filter(d->(d.key()+" "+d.label()+" "+d.type()).toLowerCase().contains(q)).toList());if(selected!=0)definitionList.getSelectionModel().select(definitionList.getItems().stream().filter(d->d.id()==selected).findFirst().orElse(null));}catch(Exception e){error(e);}}
    private void loadDefinition(Definition d){if(d==null)return;selectedDefinition=d;definitionKey.setText(d.key());definitionKey.setDisable(true);definitionLabel.setText(d.label());definitionType.setValue(d.type());definitionDefault.setText(d.defaultValue()==null?"":d.defaultValue());}
    private void newDefinition(){selectedDefinition=null;definitionList.getSelectionModel().clearSelection();definitionKey.setDisable(false);definitionKey.clear();definitionLabel.clear();definitionType.setValue(TYPES.get(0));definitionDefault.clear();}
    private void saveDefinition(){String key=definitionKey.getText().trim(),label=definitionLabel.getText().trim();if(!key.matches("[a-zA-Z][a-zA-Z0-9_]*"))throw new IllegalArgumentException("El identificador debe empezar con letra y contener letras, números o guion bajo.");if(label.isBlank())throw new IllegalArgumentException("Escriba una etiqueta.");try{Definition saved=store.saveDefinition(new Definition(selectedDefinition==null?0:selectedDefinition.id(),key,label,definitionType.getValue(),definitionDefault.getText()));selectedDefinition=saved;refreshDefinitions();refreshVariables();saved("Variable guardada en el catálogo");}catch(Exception e){error(e);}}
    private void duplicateDefinition(){Definition selected=definitionList.getSelectionModel().getSelectedItem();if(selected==null)return;try{Set<String> keys=new HashSet<>();for(Definition d:store.definitions())keys.add(d.key());String base=selected.key()+"_copia",key=base;int suffix=2;while(keys.contains(key))key=base+suffix++;Definition copy=store.saveDefinition(new Definition(0,key,selected.label()+" (copia)",selected.type(),selected.defaultValue()));selectedDefinition=copy;refreshDefinitions();definitionList.getSelectionModel().select(copy);saved("Variable duplicada con identificador independiente");}catch(Exception e){error(e);}}
    private void deleteDefinition(){Definition selected=definitionList.getSelectionModel().getSelectedItem();if(selected==null)return;try{List<String> uses=store.definitionDependencies(selected.id());if(!uses.isEmpty()){new Alert(Alert.AlertType.WARNING,"La variable se utiliza en estas plantillas:\n"+String.join("\n",uses)+"\n\nQuite primero sus vínculos antes de eliminarla.",ButtonType.OK).showAndWait();return;}Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la variable "+selected.key()+"?",ButtonType.YES,ButtonType.CANCEL);if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;store.deleteDefinition(selected.id());newDefinition();refreshDefinitions();refreshVariables();saved("Variable eliminada");}catch(Exception e){error(e);}}
    private void editDefinitionFromEditor(){Definition d=variableList.getSelectionModel().getSelectedItem();menu.getToggles().stream().filter(t->t instanceof ToggleButton b&&"Variables".equals(b.getText())).findFirst().ifPresent(menu::selectToggle);if(d!=null){selectedDefinition=d;definitionList.getSelectionModel().select(definitionList.getItems().stream().filter(x->x.id()==d.id()).findFirst().orElse(null));}}

    private BorderPane editorPage;
    private void buildEditor(){
        editorPage=new BorderPane();editorPage.getStyleClass().add("page");
        BorderPane documentPage=new BorderPane();documentPage.getStyleClass().add("page");
        HBox toolbar=new HBox(8);toolbar.setAlignment(Pos.CENTER_LEFT);
        templateChoice.setPrefWidth(240);templateChoice.setPromptText("Seleccione plantilla");
        templateChoice.setOnAction(e->{if(!updatingTemplates)safe(()->openTemplate(templateChoice.getValue()));});
        templateName.setPromptText("Nombre de la plantilla");templateName.setPrefWidth(240);
        toolbar.getChildren().addAll(templateChoice,button("Importar DOCX",this::importDocx),button("Nueva",this::newTemplate),templateName,button("Guardar plantilla",this::saveTemplate));
        toolbar.getStyleClass().add("toolbar");
        VBox top=new VBox(5,toolbar,buildEditorTools());documentPage.setTop(top);
        paletteSearch.setPromptText("Buscar variable");paletteSearch.textProperty().addListener((o,a,b)->refreshVariables());
        palettePanel=new VBox(7,new Label("VARIABLES"),paletteSearch,variableList,button("Insertar en cursor",this::insertSelected),button("Abrir Variables",this::editDefinitionFromEditor));
        palettePanel.getStyleClass().add("side-panel");palettePanel.setPrefWidth(190);palettePanel.setMinWidth(180);VBox.setVgrow(variableList,Priority.ALWAYS);
        variableList.setCellFactory(list->new ListCell<>(){
            @Override protected void updateItem(Definition v,boolean empty){super.updateItem(v,empty);setText(empty||v==null?null:v.label());setTooltip(empty||v==null?null:new Tooltip(v.label()+"\n"+v.key()+" · "+v.type()+(v.defaultValue()==null||v.defaultValue().isBlank()?"":"\nPredeterminado: "+v.defaultValue())));}
        });
        variableList.setOnDragDetected(e->{Definition v=variableList.getSelectionModel().getSelectedItem();if(v!=null){Dragboard db=variableList.startDragAndDrop(TransferMode.COPY);ClipboardContent cc=new ClipboardContent();cc.putString("definition:"+v.id());db.setContent(cc);e.consume();}});
        documentPage.setLeft(palettePanel);
        paper.getTransforms().add(paperScale);
        documentScroll.setContent(new Group(paper));documentScroll.setFitToWidth(false);documentScroll.getStyleClass().add("paper-scroll");
        documentScroll.viewportBoundsProperty().addListener((o,a,b)->updateZoom());
        documentScroll.vvalueProperty().addListener((o,a,b)->updatePageIndicator());
        BorderPane.setMargin(documentScroll,new Insets(0,0,0,10));documentPage.setCenter(documentScroll);
        editorTabs=new TabPane(new Tab("Documento",documentPage));editorTabs.getTabs().get(0).setClosable(false);editorPage.setCenter(editorTabs);
    }
    private void attachGenerationConfiguration(){Tab config=new Tab("Configurar generación",catalogPage);config.setClosable(false);editorTabs.getTabs().add(config);editorTabs.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b==config){if(dirty)safe(this::saveTemplate);refreshTemplates();refreshKinds();if(currentTemplate!=null&&kindTemplate.getValue()==null){kindTemplate.setValue(currentTemplate);}}});}
    private void showEditor(){refreshTemplates();refreshVariables();editorTabs.getSelectionModel().select(0);page("Editor de plantillas",editorPage);if(document==null)paper.getChildren().setAll(new Label("Importe un DOCX o cree una plantilla nueva."));}
    private void refreshTemplates(){try{updatingTemplates=true;Template selected=currentTemplate;templateChoice.getItems().setAll(store.templates());kindTemplate.getItems().setAll(templateChoice.getItems());if(selected!=null)templateChoice.getSelectionModel().select(templateChoice.getItems().stream().filter(t->t.id()==selected.id()).findFirst().orElse(null));}catch(Exception e){error(e);}finally{updatingTemplates=false;}}
    private void newTemplate(){try{XWPFDocument d=new XWPFDocument();d.createParagraph().createRun().setText("Escriba aquí el contenido del documento.");byte[] content=DocumentEngine.bytes(d);d.close();Template t=store.saveTemplate(new Template(0,"Nueva plantilla",content,content));data.recordRevision(t.id(),content);refreshTemplates();templateChoice.getSelectionModel().select(t.id()==0?null:templateChoice.getItems().stream().filter(x->x.id()==t.id()).findFirst().orElse(null));openTemplate(t);saved("Plantilla creada");}catch(Exception e){error(e);}}
    private void importDocx(){FileChooser fc=new FileChooser();fc.setTitle("Importar documento Word");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word DOCX","*.docx"));File file=fc.showOpenDialog(stage);if(file==null)return;try{byte[] content=Files.readAllBytes(file.toPath());try(XWPFDocument check=DocumentEngine.open(content)){}Template t=store.saveTemplate(new Template(0,file.getName().replaceFirst("(?i)\\.docx$",""),content,content));data.recordRevision(t.id(),content);refreshTemplates();templateChoice.getSelectionModel().select(templateChoice.getItems().stream().filter(x->x.id()==t.id()).findFirst().orElse(null));openTemplate(t);saved("DOCX importado y guardado localmente");}catch(Exception e){error(e);}}
    private void openTemplate(Template t){if(t==null)return;try{if(document!=null&&dirty)saveTemplate();if(document!=null)document.close();currentTemplate=t;document=DocumentEngine.open(t.edited());selectedRun=null;selectedParagraph=null;selectedText=null;templateName.setText(t.name());dirty=false;refreshVariables();renderDocument();updatingTemplates=true;templateChoice.getSelectionModel().select(templateChoice.getItems().stream().filter(x->x.id()==t.id()).findFirst().orElse(t));updatingTemplates=false;}catch(Exception e){updatingTemplates=false;error(e);}}
    private void saveTemplate(){if(document==null)return;if(templateName.getText().isBlank())throw new IllegalArgumentException("Escriba un nombre de plantilla.");try{byte[] content=DocumentEngine.bytes(document);currentTemplate=store.saveTemplate(new Template(currentTemplate.id(),templateName.getText().trim(),currentTemplate.original(),content));data.recordRevision(currentTemplate.id(),content);dirty=false;refreshTemplates();saved("Plantilla guardada");}catch(Exception e){throw new RuntimeException("No se pudo guardar la plantilla",e);}}
    private void refreshVariables(){try{long selected=variableList.getSelectionModel().getSelectedItem()==null?0:variableList.getSelectionModel().getSelectedItem().id();String q=paletteSearch.getText().trim().toLowerCase();variableList.getItems().setAll(store.definitions().stream().filter(d->(d.label()+" "+d.key()).toLowerCase().contains(q)).toList());if(selected!=0)variableList.getSelectionModel().select(variableList.getItems().stream().filter(d->d.id()==selected).findFirst().orElse(null));}catch(Exception e){error(e);}}
    private void insertSelected(){Definition d=variableList.getSelectionModel().getSelectedItem();if(d==null||selectedText==null){saved("Seleccione una variable y coloque el cursor en el documento");return;}insertToken(selectedText,"definition:"+d.id());}
    private Node buildEditorTools(){
        fontChoice.setEditable(true);fontChoice.setPrefWidth(145);fontChoice.getItems().setAll("Arial","Calibri","Cambria","Georgia","Times New Roman","Segoe UI");fontChoice.setPromptText("Fuente");
        ColorPicker color=new ColorPicker(Color.BLACK);color.setOnAction(e->{if(selectedRun!=null){selectedRun.setColor(color.getValue().toString().substring(2,8));changed();}});
        ColorPicker highlight=new ColorPicker(Color.YELLOW);highlight.setOnAction(e->{if(selectedRun!=null){var props=selectedRun.getCTR().isSetRPr()?selectedRun.getCTR().getRPr():selectedRun.getCTR().addNewRPr();var shading=props.sizeOfShdArray()>0?props.getShdArray(0):props.addNewShd();shading.setFill(highlight.getValue().toString().substring(2,8).toUpperCase());changed();}});
        HBox textTools=new HBox(5,fontChoice,button("Aplicar fuente",()->{if(selectedRun!=null&&fontChoice.getValue()!=null){selectedRun.setFontFamily(fontChoice.getValue());changed();}}),button("N",()->styleRun("bold")),button("C",()->styleRun("italic")),button("S",()->styleRun("underline")),button("T",()->styleRun("strike")),button("A−",()->styleRun("smaller")),button("A+",()->styleRun("larger")),new Label("Color"),color,new Label("Resaltar"),highlight,button("Copiar",()->{if(selectedText!=null)selectedText.copy();}),button("Cortar",()->{if(selectedText!=null)selectedText.cut();}),button("Pegar",()->{if(selectedText!=null)selectedText.paste();}),button("Deshacer",()->{if(selectedText!=null)selectedText.undo();}),button("Rehacer",()->{if(selectedText!=null)selectedText.redo();}));
        HBox paragraphTools=new HBox(5,button("←",()->align(ParagraphAlignment.LEFT)),button("≡",()->align(ParagraphAlignment.CENTER)),button("→",()->align(ParagraphAlignment.RIGHT)),button("Justificar",()->align(ParagraphAlignment.BOTH)),button("Interlineado 1",()->setLineSpacing(1)),button("Interlineado 1,5",()->setLineSpacing(1.5)),button("Espacio antes +",()->adjustParagraphSpacing(true,120)),button("Espacio después +",()->adjustParagraphSpacing(false,120)),button("Sangría +",()->adjustIndent(360)),button("Sangría −",()->adjustIndent(-360)),button("Título",()->setHeading("Heading1")),button("Subtítulo",()->setHeading("Heading2")),button("Limpiar formato",this::clearFormat));
        VBox home=new VBox(2,textTools,paragraphTools);
        HBox insert=new HBox(5,button("Insertar variable",this::insertSelected),button("Insertar imagen",()->insertImage(false)),button("Insertar tabla",this::insertTable),button("Añadir fila",()->adjustTable(true,true)),button("Quitar fila",()->adjustTable(true,false)),button("Añadir columna",()->adjustTable(false,true)),button("Quitar columna",()->adjustTable(false,false)),button("Salto de página",this::insertPageBreak));
        paperChoice.getItems().setAll("A4","Carta");paperChoice.setValue("A4");paperChoice.setOnAction(e->{if(!readingPageSize)setPaperSize();});
        HBox design=new HBox(5,new Label("Papel"),paperChoice,button("Vertical / horizontal",this::toggleOrientation),button("Márgenes normales",()->setMargins(1440)),button("Márgenes estrechos",()->setMargins(720)));
        HBox headers=new HBox(5,button("Agregar encabezado",()->addHeaderFooter(true)),button("Imagen en encabezado",()->insertImage(true)),button("Agregar pie",()->addHeaderFooter(false)),button("Número de página",this::insertPageNumber));
        zoomChoice.getItems().setAll("Ancho","Página","75%","100%","125%","150%","200%");zoomChoice.setValue("Ancho");zoomChoice.setOnAction(e->{zoomMode=zoomChoice.getValue();updateZoom();});
        paletteToggle=button("Contraer variables",()->{boolean visible=!palettePanel.isVisible();palettePanel.setVisible(visible);palettePanel.setManaged(visible);paletteToggle.setText(visible?"Contraer variables":"Mostrar variables");Platform.runLater(this::updateZoom);});
        HBox view=new HBox(7,paletteToggle,zoomChoice,pageIndicator);
        TabPane tools=new TabPane();Map<String,Node> sections=new LinkedHashMap<>();sections.put("Inicio",home);sections.put("Insertar",insert);sections.put("Diseño",design);sections.put("Encabezado y pie",headers);sections.put("Vista",view);for(var entry:sections.entrySet()){ScrollPane strip=new ScrollPane(entry.getValue());strip.setFitToHeight(true);strip.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);strip.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);Tab tab=new Tab(entry.getKey(),strip);tab.setClosable(false);tools.getTabs().add(tab);}tools.setMaxHeight(140);return tools;
    }
    private void setLineSpacing(double spacing){if(selectedParagraph==null)return;selectedParagraph.setSpacingBetween(spacing);changed();}
    private void adjustParagraphSpacing(boolean before,int change){if(selectedParagraph==null)return;if(before)selectedParagraph.setSpacingBefore(Math.max(0,selectedParagraph.getSpacingBefore()+change));else selectedParagraph.setSpacingAfter(Math.max(0,selectedParagraph.getSpacingAfter()+change));changed();}
    private void adjustIndent(int change){if(selectedParagraph==null)return;selectedParagraph.setIndentationLeft(Math.max(0,selectedParagraph.getIndentationLeft()+change));changed();}
    private void setHeading(String style){if(selectedParagraph==null)return;selectedParagraph.setStyle(style);changed();}
    private void clearFormat(){if(selectedRun==null)return;selectedRun.setBold(false);selectedRun.setItalic(false);selectedRun.setUnderline(UnderlinePatterns.NONE);selectedRun.setStrikeThrough(false);selectedRun.setColor("000000");selectedRun.setTextHighlightColor("none");if(selectedRun.getCTR().isSetRPr()){var props=selectedRun.getCTR().getRPr();while(props.sizeOfShdArray()>0)props.removeShd(0);}changed();}
    private void adjustTable(boolean row,boolean add){if(selectedParagraph==null||!(selectedParagraph.getBody() instanceof XWPFTableCell cell))return;XWPFTable table=cell.getTableRow().getTable();if(row){if(add)table.createRow();else if(table.getNumberOfRows()>1)table.removeRow(table.getRows().indexOf(cell.getTableRow()));}else{int column=cell.getTableRow().getTableCells().indexOf(cell);for(XWPFTableRow r:table.getRows()){if(add)r.addNewTableCell();else if(r.getTableCells().size()>1)r.removeCell(Math.min(column,r.getTableCells().size()-1));}}changed();}
    private void changed(){dirty=true;status.setText("Cambios sin guardar");renderDocument();}
    private void updateZoom(){double viewport=documentScroll.getViewportBounds().getWidth();if(viewport<=0)return;double scale=switch(zoomMode){case "Página"->Math.min((viewport-24)/pageWidth,(documentScroll.getViewportBounds().getHeight()-24)/pageHeight);case "Ancho"->(viewport-24)/pageWidth;default->Double.parseDouble(zoomMode.replace("%",""))/100.0;};scale=Math.max(.25,Math.min(3,scale));paperScale.setX(scale);paperScale.setY(scale);updatePageIndicator();}
    private void updatePageIndicator(){int count=Math.max(1,paper.getChildren().size());double offset=documentScroll.getVvalue()*Math.max(0,paper.getHeight()*paperScale.getY()-documentScroll.getViewportBounds().getHeight());int current=Math.min(count,Math.max(1,1+(int)(offset/((pageHeight+10)*paperScale.getY()))));pageIndicator.setText("Página "+current+" de "+count);}
    private void setPaperSize(){if(document==null)return;pageWidth="Carta".equals(paperChoice.getValue())?816:794;pageHeight="Carta".equals(paperChoice.getValue())?1056:1123;var sect=document.getDocument().getBody().isSetSectPr()?document.getDocument().getBody().getSectPr():document.getDocument().getBody().addNewSectPr();var size=sect.isSetPgSz()?sect.getPgSz():sect.addNewPgSz();size.setW(java.math.BigInteger.valueOf(Math.round(pageWidth*15)));size.setH(java.math.BigInteger.valueOf(Math.round(pageHeight*15)));changed();}
    private void toggleOrientation(){if(document==null)return;double w=pageWidth;pageWidth=pageHeight;pageHeight=w;var sect=document.getDocument().getBody().isSetSectPr()?document.getDocument().getBody().getSectPr():document.getDocument().getBody().addNewSectPr();var size=sect.isSetPgSz()?sect.getPgSz():sect.addNewPgSz();size.setW(java.math.BigInteger.valueOf(Math.round(pageWidth*15)));size.setH(java.math.BigInteger.valueOf(Math.round(pageHeight*15)));changed();}
    private void setMargins(int twips){if(document==null)return;var sect=document.getDocument().getBody().isSetSectPr()?document.getDocument().getBody().getSectPr():document.getDocument().getBody().addNewSectPr();var margin=sect.isSetPgMar()?sect.getPgMar():sect.addNewPgMar();long n=twips;margin.setLeft(java.math.BigInteger.valueOf(n));margin.setRight(java.math.BigInteger.valueOf(n));margin.setTop(java.math.BigInteger.valueOf(n));margin.setBottom(java.math.BigInteger.valueOf(n));changed();}
    private void insertTable(){if(document==null)return;document.createTable(2,2);changed();}
    private void insertPageBreak(){if(document==null)return;XWPFParagraph p=document.createParagraph();p.setPageBreak(true);changed();}
    private void addHeaderFooter(boolean header){if(document==null)return;XWPFHeaderFooter part=header?(document.getHeaderList().isEmpty()?document.createHeader(HeaderFooterType.DEFAULT):document.getHeaderList().get(0)):(document.getFooterList().isEmpty()?document.createFooter(HeaderFooterType.DEFAULT):document.getFooterList().get(0));part.createParagraph().createRun().setText(header?"Encabezado":"Pie de página");changed();}
    private void insertPageNumber(){if(document==null)return;XWPFFooter footer=document.getFooterList().isEmpty()?document.createFooter(HeaderFooterType.DEFAULT):document.getFooterList().get(0);XWPFRun run=footer.createParagraph().createRun();run.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN);XWPFRun instruction=footer.getParagraphs().get(footer.getParagraphs().size()-1).createRun();instruction.getCTR().addNewInstrText().setStringValue(" PAGE ");XWPFRun end=footer.getParagraphs().get(footer.getParagraphs().size()-1).createRun();end.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);changed();}
    private void insertImage(boolean header){if(document==null)return;FileChooser chooser=new FileChooser();chooser.setTitle("Insertar imagen");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG/JPG","*.png","*.jpg","*.jpeg"));File file=chooser.showOpenDialog(stage);if(file==null)return;try(InputStream in=Files.newInputStream(file.toPath())){XWPFParagraph p;if(header){XWPFHeader h=document.getHeaderList().isEmpty()?document.createHeader(HeaderFooterType.DEFAULT):document.getHeaderList().get(0);p=h.createParagraph();}else p=selectedParagraph==null?document.createParagraph():selectedParagraph;Image source=new Image(file.toURI().toString());if(source.isError()||source.getWidth()<=0)throw new IOException("No se pudo leer la imagen.");double width=Math.min(300,source.getWidth()*72/96),height=width*source.getHeight()/source.getWidth();if(height>600){width*=600/height;height=600;}int type=file.getName().toLowerCase().endsWith(".png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;p.createRun().addPicture(in,type,file.getName(),Units.toEMU(width),Units.toEMU(height));changed();}catch(Exception e){error(e);}}
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

    private void renderDocument(){paper.getChildren().clear();selectedText=null;if(document==null)return;
        pageWidth=794;pageHeight=1123;var sect=document.getDocument().getBody().getSectPr();if(sect!=null&&sect.isSetPgSz()){pageWidth=((Number)sect.getPgSz().getW()).doubleValue()/15.0;pageHeight=((Number)sect.getPgSz().getH()).doubleValue()/15.0;}readingPageSize=true;paperChoice.setValue(Math.abs(pageWidth-816)<15?"Carta":"A4");readingPageSize=false;
        double margin=sect!=null&&sect.isSetPgMar()&&sect.getPgMar().getLeft()!=null?((Number)sect.getPgMar().getLeft()).doubleValue()/15.0:72;
        double top=sect!=null&&sect.isSetPgMar()&&sect.getPgMar().getTop()!=null?((Number)sect.getPgMar().getTop()).doubleValue()/15.0:72;
        paper.setPrefWidth(pageWidth);paper.setMaxWidth(pageWidth);paper.setMinWidth(pageWidth);
        VBox page=newPage(margin,top);double used=top+80;
        for(IBodyElement element:document.getBodyElements()){
            if(element instanceof XWPFParagraph p){Node node=renderParagraph(p,true);double height=estimateHeight(node);if(p.isPageBreak()||used+height>pageHeight-top){page=newPage(margin,top);used=top+80;}page.getChildren().add(node);used+=height;}
            else if(element instanceof XWPFTable t){VBox table=new VBox(2);table.getStyleClass().add("doc-table");for(XWPFTableRow row:t.getRows()){HBox cells=new HBox(2);for(XWPFTableCell cell:row.getTableCells()){VBox cellBox=new VBox(2);cellBox.getStyleClass().add("doc-cell");for(XWPFParagraph p:cell.getParagraphs())cellBox.getChildren().add(renderParagraph(p,true));HBox.setHgrow(cellBox,Priority.ALWAYS);cells.getChildren().add(cellBox);}table.getChildren().add(cells);}double height=Math.max(40,t.getRows().size()*45);if(used+height>pageHeight-top){page=newPage(margin,top);used=top+80;}page.getChildren().add(table);used+=height;}
        }
        Platform.runLater(this::updateZoom);
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
    private void refreshKinds(){try{String q=kindSearch.getText().toLowerCase();kindList.getItems().setAll(store.kinds().stream().filter(k->k.title().toLowerCase().contains(q)||(k.category()!=null&&k.category().toLowerCase().contains(q))).toList());generateKind.getItems().setAll(store.kinds().stream().filter(Kind::active).toList());}catch(Exception e){error(e);}}
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
    private void buildGenerator(){generatorPage=new BorderPane();generatorPage.getStyleClass().add("page");HBox top=new HBox(12,new Label("Documento"),generateKind);top.setAlignment(Pos.CENTER_LEFT);top.getStyleClass().add("toolbar");generateKind.setPrefWidth(360);generateKind.setOnAction(e->safe(()->{historicalTemplate=null;buildDynamicForm();}));generatorPage.setTop(top);
        ScrollPane formScroll=new ScrollPane(generatedForm);formScroll.setFitToWidth(true);generatedForm.getStyleClass().add("generator-form");generatorPage.setCenter(formScroll);
        VBox right=new VBox(12,new Label("VISTA PREVIA"),new ScrollPane(preview),button("Previsualizar",this::makePreview),button("Guardar DOCX",this::saveGenerated),button("Guardar PDF",this::savePdf));right.getStyleClass().add("side-panel");right.setPrefWidth(400);VBox.setVgrow(right.getChildren().get(1),Priority.ALWAYS);preview.getStyleClass().add("preview");generatorPage.setRight(right);
    }
    private void showGenerator(){refreshKinds();page("Generar documentos",generatorPage);if(generateKind.getValue()==null)generatedForm.getChildren().setAll(new Label("Seleccione un documento. Puede publicarlo desde Editor de plantillas → Configurar generación."));}
    private void buildDynamicForm(){Kind k=generateKind.getValue();generatedBytes=null;generatedPdf=null;generatedHistoryId=0;preview.getChildren().clear();inputs.clear();localValueOverrides.clear();generatedForm.getChildren().clear();if(k==null)return;try{List<Variable> vars=store.variables(k.templateId());Map<String,Boolean> required=store.kindRequirements(k.id());Map<String,String> defaults=store.kindDefaults(k.id());Map<String,Variable> byKey=new LinkedHashMap<>();for(Variable v:vars)byKey.put(v.key(),v);List<String> order=k.fields()==null||k.fields().isBlank()?List.of():Arrays.asList(k.fields().split(","));for(String key:order){Variable v=byKey.get(key);if(v==null)continue;Control input;if(v.type().equals("Texto largo")||v.type().equals("Domicilio")){TextArea a=new TextArea(defaults.getOrDefault(v.key(),v.defaultValue()));a.setPrefRowCount(3);input=a;}else{TextField f=new TextField(defaults.getOrDefault(v.key(),v.defaultValue()));input=f;}inputs.put(v.key(),input);Node control=input;
                if((v.type().equals("DNI")||v.type().equals("RUC"))&&input instanceof TextField field){HBox row=new HBox(6,field,button("Consultar",()->lookupForField(k,v,field)));HBox.setHgrow(field,Priority.ALWAYS);control=row;}
                generatedForm.getChildren().add(labelled(v.label()+(required.getOrDefault(v.key(),v.required())?" *":"")+" · "+v.type(),control));}addContactSelectors(k);if(inputs.isEmpty())generatedForm.getChildren().add(new Label("Este tipo documental no muestra campos. Revise su configuración y los valores predeterminados."));}catch(Exception e){error(e);}}
    private void addContactSelectors(Kind k)throws Exception{
        Map<String,List<ContactBinding>> groups=new LinkedHashMap<>();for(ContactBinding binding:data.contactBindings(k.templateId()))groups.computeIfAbsent(binding.groupKey(),ignored->new ArrayList<>()).add(binding);
        for(var entry:groups.entrySet()){
            List<ContactBinding> group=entry.getValue();String kind=group.get(0).kind();TextField search=new TextField();search.setPromptText("Buscar por nombre o documento");
            ComboBox<Contact> choice=new ComboBox<>();choice.setPromptText("Seleccionar "+kind.toLowerCase());choice.setPrefWidth(250);
            Runnable filter=()->{try{String q=search.getText().trim().toLowerCase();choice.getItems().setAll(data.contacts().stream().filter(c->kind.equals(c.kind())).filter(c->(c.given()+" "+c.family()+" "+c.number()).toLowerCase().contains(q)).toList());}catch(Exception ex){error(ex);}};
            search.textProperty().addListener((o,a,b)->filter.run());choice.setOnShowing(e->filter.run());filter.run();
            HBox row=new HBox(8,search,choice,button("Usar registro",()->{Contact selected=choice.getValue();if(selected==null)return;Map<String,String> values=ContactReuse.valuesFor(selected,group);for(var mapped:values.entrySet()){Control target=inputs.get(mapped.getKey());if(target instanceof TextArea area)area.setText(mapped.getValue());else if(target instanceof TextField field)field.setText(mapped.getValue());else localValueOverrides.put(mapped.getKey(),mapped.getValue());}saved("Datos locales aplicados a "+entry.getKey()+"; puede editarlos antes de generar");}));
            HBox.setHgrow(search,Priority.ALWAYS);generatedForm.getChildren().add(labelled("REGISTRO LOCAL · "+entry.getKey()+" · "+kind,row));
        }
    }
    private void makePreview(){
        generatedBytes=null;generatedPdf=null;Kind k=generateKind.getValue();if(k==null)throw new IllegalArgumentException("Seleccione un tipo documental.");
        try{
            Map<String,String> values=new LinkedHashMap<>();Map<String,Variable> vars=new HashMap<>();Map<String,Boolean> required=store.kindRequirements(k.id());Map<String,String> defaults=store.kindDefaults(k.id());
            for(Variable v:store.variables(k.templateId())){vars.put(v.key(),v);values.put(v.key(),defaults.getOrDefault(v.key(),v.defaultValue()==null?"":v.defaultValue()));}
            values.putAll(localValueOverrides);
            for(var entry:inputs.entrySet()){
                Control input=entry.getValue();String value=input instanceof TextArea a?a.getText():((TextField)input).getText();Variable v=vars.get(entry.getKey());
                if(v==null)throw new IllegalArgumentException("Variable sin definir: "+entry.getKey());
                if(required.getOrDefault(v.key(),v.required())&&value.isBlank())throw new IllegalArgumentException("Complete el campo: "+v.label());values.put(entry.getKey(),value);
            }
            if(!values.equals(generatedValues))generatedHistoryId=0;
            Template t=store.templates().stream().filter(x->x.id()==k.templateId()).findFirst().orElseThrow();byte[] source=historicalTemplate==null?t.edited():historicalTemplate;
            try(XWPFDocument doc=DocumentEngine.open(source)){
                for(String marker:DocumentEngine.markers(doc)){
                    Variable v=vars.get(marker);if(v==null)throw new IllegalArgumentException("Variable sin definir: "+marker);
                    if(values.get(marker).isBlank()&&!inputs.containsKey(marker))throw new IllegalArgumentException("La variable '"+marker+"' no está en el formulario ni tiene valor predeterminado.");
                }
            }
            generatedBytes=DocumentEngine.generate(source,values);generatedValues=values;preview.getChildren().clear();
            try(XWPFDocument result=DocumentEngine.open(generatedBytes)){
                for(IBodyElement element:result.getBodyElements()){
                    if(element instanceof XWPFParagraph p)preview.getChildren().add(renderParagraph(p,false));
                    else if(element instanceof XWPFTable table)for(XWPFTableRow row:table.getRows()){HBox cells=new HBox(8);for(XWPFTableCell cell:row.getTableCells())cells.getChildren().add(new Label(cell.getText()));preview.getChildren().add(cells);}
                }
            }
            saved("Vista previa generada; puede modificar valores y generar nuevamente");
        }catch(Exception e){error(e);}
    }
    private void saveGenerated(){makePreview();if(generatedBytes==null)return;FileChooser fc=new FileChooser();fc.setTitle("Guardar documento generado");fc.setInitialFileName(fileName()+".docx");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word DOCX","*.docx"));File target=fc.showSaveDialog(stage);if(target==null)return;try{Files.write(target.toPath(),generatedBytes);recordGeneration(target.toString(),null,null);saved("DOCX guardado: "+target.getName()+contactSaveSummary);}catch(Exception e){error(e);}}
    private void savePdf(){makePreview();if(generatedBytes==null)return;FileChooser fc=new FileChooser();fc.setTitle("Guardar PDF");fc.setInitialFileName(fileName()+".pdf");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));File target=fc.showSaveDialog(stage);if(target==null)return;try{generatedPdf=DocxPdfExporter.render(generatedBytes);Files.write(target.toPath(),generatedPdf);recordGeneration(null,target.toString(),generatedPdf);saved("PDF guardado: "+target.getName()+contactSaveSummary);}catch(Exception e){error(e);}}
    private String fileName(){return generateKind.getValue().title().replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚ _-]","");}
    private void recordGeneration(String docxPath,String pdfPath,byte[] pdf)throws Exception{Kind k=generateKind.getValue();Template t=store.templates().stream().filter(x->x.id()==k.templateId()).findFirst().orElseThrow();int version=data.recordRevision(t.id(),historicalTemplate==null?t.edited():historicalTemplate);if(generatedHistoryId==0){History h=data.saveHistory(new History(0,k.title(),t.id(),version,null,json.writeValueAsString(generatedValues),docxPath,pdfPath,generatedBytes,pdf));generatedHistoryId=h.id();}else data.updateHistoryFiles(generatedHistoryId,docxPath,pdfPath,pdf);autoSaveContacts(k);}
    private void autoSaveContacts(Kind kind){contactSaveSummary="";try{List<ContactBinding> bindings=data.contactBindings(kind.templateId());if(bindings.isEmpty())return;Map<String,String> types=new HashMap<>();for(Variable v:store.variables(kind.templateId()))types.put(v.key(),v.type());ContactReuse.Result result=ContactReuse.save(data,bindings,generatedValues,types,(old,next)->{Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"El registro "+old.number()+" ya tiene datos diferentes. ¿Actualizarlo con los valores del documento?",ButtonType.YES,ButtonType.NO);return confirm.showAndWait().orElse(ButtonType.NO)==ButtonType.YES;});contactSaveSummary=" · Agenda: "+result.created()+" nuevos, "+result.updated()+" actualizados"+(result.skipped()>0?", "+result.skipped()+" sin identificador suficiente":"");}catch(Exception e){contactSaveSummary=" · No se pudo actualizar la agenda: "+e.getMessage();}}

    private void buildContacts(){contactsPage=new BorderPane();contactsPage.getStyleClass().add("page");contactSearch.setPromptText("Buscar nombre, documento o dirección");VBox left=new VBox(8,contactSearch,contactList,button("+ Nuevo",this::newContact),button("Eliminar",this::deleteContact));left.getStyleClass().add("side-panel");left.setPrefWidth(300);VBox.setVgrow(contactList,Priority.ALWAYS);contactsPage.setLeft(left);contactSearch.textProperty().addListener((o,a,b)->refreshContacts());contactList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->loadContact(b));
        contactKind.getItems().setAll("Persona","Empresa");contactKind.setValue("Persona");contactKind.setOnAction(e->{boolean company="Empresa".equals(contactKind.getValue());contactFamily.setDisable(company);contactDocType.setDisable(company);});contactRepresentatives.setPrefRowCount(2);contactExtras.setPrefRowCount(4);contactExtras.setText("{}");
        VBox form=new VBox(12,new Label("FICHA LOCAL"),labelled("Tipo",contactKind),labelled("Nombres / razón social",contactGiven),labelled("Apellidos",contactFamily),labelled("Tipo de documento",contactDocType),labelled("Número de documento / RUC",contactNumber),labelled("Domicilio / domicilio fiscal",contactAddress),labelled("Representantes (empresa)",contactRepresentatives),labelled("Datos adicionales · JSON",contactExtras),button("Guardar registro",this::saveContact));form.getStyleClass().add("catalog-form");ScrollPane scroll=new ScrollPane(form);scroll.setFitToWidth(true);contactsPage.setCenter(scroll);}
    private void showContacts(){refreshContacts();page("Personas y empresas",contactsPage);}
    private void refreshContacts(){try{String q=contactSearch.getText().toLowerCase();contactList.getItems().setAll(data.contacts().stream().filter(c->(c.given()+" "+c.family()+" "+c.number()+" "+c.address()).toLowerCase().contains(q)).toList());}catch(Exception e){error(e);}}
    private void newContact(){selectedContact=null;contactList.getSelectionModel().clearSelection();contactKind.setValue("Persona");contactGiven.clear();contactFamily.clear();contactDocType.setText("DNI");contactNumber.clear();contactAddress.clear();contactRepresentatives.clear();contactExtras.setText("{}");}
    private void loadContact(Contact c){if(c==null)return;selectedContact=c;contactKind.setValue(c.kind());contactGiven.setText(c.given());contactFamily.setText(c.family());contactDocType.setText(c.documentType());contactNumber.setText(c.number());contactAddress.setText(c.address());contactRepresentatives.setText(c.representatives());contactExtras.setText(c.extrasJson());}
    private void saveContact(){if(contactGiven.getText().isBlank())throw new IllegalArgumentException("Escriba nombre o razón social.");try{if(!json.readTree(contactExtras.getText()).isObject())throw new IllegalArgumentException("Los datos adicionales deben ser un objeto JSON.");Contact c=data.saveContact(new Contact(selectedContact==null?0:selectedContact.id(),contactKind.getValue(),contactGiven.getText().trim(),contactFamily.getText().trim(),contactDocType.getText().trim(),contactNumber.getText().trim(),contactAddress.getText().trim(),contactRepresentatives.getText().trim(),contactExtras.getText()));refreshContacts();contactList.getSelectionModel().select(c);saved("Registro local guardado");}catch(Exception e){error(e);}}
    private void deleteContact(){Contact c=contactList.getSelectionModel().getSelectedItem();if(c==null)return;try{data.deleteContact(c.id());newContact();refreshContacts();saved("Registro eliminado");}catch(Exception e){error(e);}}

    private void buildApi(){apiPage=new BorderPane();apiPage.getStyleClass().add("page");apiToken.setPromptText(secret.configured()?"Token guardado; escriba para reemplazar":"Token Bearer de Factiliza");apiType.getItems().setAll("DNI","RUC");apiType.setValue("DNI");apiNumber.setPromptText("Número a consultar");
        HBox query=new HBox(8,apiType,apiNumber,button("Consultar / probar conexión",()->runApiQuery(apiType.getValue(),apiNumber.getText(),this::showApiResult)));HBox.setHgrow(apiNumber,Priority.ALWAYS);
        VBox left=new VBox(12,new Label("FACTILIZA · CONEXIÓN OPCIONAL"),labelled("Token",apiToken),button("Guardar token local",()->{try{secret.save(apiToken.getText());apiToken.clear();apiToken.setPromptText("Token guardado; escriba para reemplazar");saved("Token protegido localmente");}catch(Exception e){error(e);}}),button("Eliminar token",()->{try{secret.delete();saved("Token eliminado");}catch(Exception e){error(e);}}),query,apiState,new ScrollPane(apiResultBox),button("Guardar resultado como registro local",this::saveApiContact));left.getStyleClass().add("catalog-form");ScrollPane leftScroll=new ScrollPane(left);leftScroll.setFitToWidth(true);apiPage.setCenter(leftScroll);
        mapTemplate.setPrefWidth(210);mapTemplate.setOnAction(e->refreshMappings());mapSource.setPrefWidth(180);mapTarget.setPrefWidth(180);mapSource.setOnAction(e->refreshApiFieldChoices());mapApiField.setEditable(true);mapApiField.setPrefWidth(180);
        VBox right=new VBox(10,new Label("CORRESPONDENCIAS EXPLÍCITAS"),new Label("Origen DNI/RUC → dato de Factiliza → variable de destino"),labelled("Plantilla",mapTemplate),labelled("Campo que consulta",mapSource),labelled("Dato de respuesta",mapApiField),labelled("Variable a completar",mapTarget),button("Guardar correspondencia",this::saveMapping),mappingList,button("Eliminar correspondencia",this::deleteMapping));right.getStyleClass().add("side-panel");right.setPrefWidth(300);VBox.setVgrow(mappingList,Priority.ALWAYS);apiPage.setRight(right);}
    private void showApi(){refreshTemplates();mapTemplate.getItems().setAll(templateChoice.getItems());page("Conexiones API",apiPage);apiState.setText(secret.configured()?"Token configurado · Consultas opcionales":"Sin token · El sistema funciona sin conexión");}
    private void runApiQuery(String type,String number,java.util.function.Consumer<FactilizaClient.Result> onSuccess){apiState.setText("Consultando…");Task<FactilizaClient.Result> task=new Task<>(){@Override protected FactilizaClient.Result call()throws Exception{return factiliza.query(type,number,secret.load());}};task.setOnSucceeded(e->{FactilizaClient.Result result=task.getValue();apiState.setText("Consulta correcta: "+result.message());onSuccess.accept(result);});task.setOnFailed(e->{Throwable failure=task.getException();apiState.setText("Consulta fallida: "+failure.getMessage());});Thread thread=new Thread(task,"factiliza-consulta");thread.setDaemon(true);thread.start();}
    private void showApiResult(FactilizaClient.Result result){lastApiType=result.type();apiResultFields.clear();apiResultBox.getChildren().clear();result.data().forEach((key,value)->{TextField field=new TextField(value);apiResultFields.put(key,field);apiResultBox.getChildren().add(labelled(key,field));});saved("Respuesta cargada; puede corregir los datos antes de guardarlos");}
    private void saveApiContact(){if(apiResultFields.isEmpty())throw new IllegalArgumentException("Consulte primero un DNI o RUC.");Map<String,String> fields=new LinkedHashMap<>();apiResultFields.forEach((key,field)->fields.put(key,field.getText()));boolean company="RUC".equals(lastApiType);String name=company?fields.getOrDefault("nombre_o_razon_social",""):fields.getOrDefault("nombres","");String family=(fields.getOrDefault("apellido_paterno","")+" "+fields.getOrDefault("apellido_materno","")).trim();try{Contact c=data.saveContact(new Contact(0,company?"Empresa":"Persona",name,company?"":family,company?"RUC":"DNI",fields.getOrDefault("numero",""),fields.getOrDefault("direccion_completa",fields.getOrDefault("direccion","")),"",json.writeValueAsString(fields)));saved("Resultado guardado como registro local: "+c);refreshContacts();}catch(Exception e){error(e);}}
    private void refreshMappings(){Template t=mapTemplate.getValue();try{if(t==null){mapSource.getItems().clear();mapTarget.getItems().clear();mappingList.getItems().clear();return;}List<Variable> vars=store.variables(t.id());mapSource.getItems().setAll(vars.stream().filter(v->v.type().equals("DNI")||v.type().equals("RUC")).toList());mapTarget.getItems().setAll(vars);mappingList.getItems().setAll(data.mappings(t.id()));}catch(Exception e){error(e);}}
    private void refreshApiFieldChoices(){Variable source=mapSource.getValue();if(source==null)return;if(source.type().equals("DNI"))mapApiField.getItems().setAll("numero","nombres","apellido_paterno","apellido_materno","nombre_completo","direccion","direccion_completa","departamento","provincia","distrito");else mapApiField.getItems().setAll("numero","nombre_o_razon_social","direccion","direccion_completa","tipo_contribuyente","estado","condicion","departamento","provincia","distrito");}
    private void saveMapping(){Template t=mapTemplate.getValue();Variable source=mapSource.getValue(),target=mapTarget.getValue();String field=mapApiField.getValue();if(t==null||source==null||target==null||field==null||field.isBlank())throw new IllegalArgumentException("Seleccione plantilla, origen, dato y destino.");try{data.saveMapping(new Mapping(0,t.id(),source.key(),field.trim(),target.key()));refreshMappings();saved("Correspondencia guardada");}catch(Exception e){error(e);}}
    private void deleteMapping(){Mapping m=mappingList.getSelectionModel().getSelectedItem();if(m==null)return;try{data.deleteMapping(m.id());refreshMappings();saved("Correspondencia eliminada");}catch(Exception e){error(e);}}
    private void lookupForField(Kind k,Variable variable,TextField field){runApiQuery(variable.type(),field.getText(),r->{applyMapping(k.templateId(),variable.key(),r.data());saved("Consulta completada para "+variable.label()+"; revise los valores rellenados");});}
    private void applyMapping(long templateId,String sourceKey,Map<String,String> values){try{for(Mapping m:data.mappings(templateId))if(m.sourceKey().equals(sourceKey)){Control target=inputs.get(m.targetKey());String value=values.get(m.apiField());if(target!=null&&value!=null){if(target instanceof TextArea area)area.setText(value);else ((TextField)target).setText(value);}}}catch(Exception e){error(e);}}

    private void buildHistory(){historyPage=new BorderPane();historyPage.getStyleClass().add("page");historySearch.setPromptText("Buscar por título o fecha");historySearch.textProperty().addListener((o,a,b)->refreshHistory());historyDate.setPromptText("Filtrar fecha");historyDate.valueProperty().addListener((o,a,b)->refreshHistory());VBox list=new VBox(8,historySearch,historyDate,button("Quitar filtro",()->{historySearch.clear();historyDate.setValue(null);}),historyList);list.getStyleClass().add("side-panel");list.setPrefWidth(360);VBox.setVgrow(historyList,Priority.ALWAYS);historyPage.setLeft(list);
        VBox actions=new VBox(13,new Label("DOCUMENTO SELECCIONADO"),button("Abrir DOCX",this::openHistory),button("Duplicar registro",this::duplicateHistory),button("Editar valores y regenerar",this::editHistory),button("Exportar DOCX nuevamente",()->exportHistory(false)),button("Exportar PDF nuevamente",()->exportHistory(true)),button("Eliminar registro",this::deleteHistory));actions.getStyleClass().add("catalog-form");historyPage.setCenter(actions);}
    private void showHistory(){refreshHistory();page("Historial",historyPage);}
    private void refreshHistory(){try{String q=historySearch.getText().toLowerCase();String date=historyDate.getValue()==null?null:historyDate.getValue().toString();historyList.getItems().setAll(data.history().stream().filter(h->(h.title()+" "+h.generatedAt()).toLowerCase().contains(q)).filter(h->date==null||h.generatedAt().startsWith(date)).toList());}catch(Exception e){error(e);}}
    private History selectedHistory(){History h=historyList.getSelectionModel().getSelectedItem();if(h==null)throw new IllegalArgumentException("Seleccione un documento del historial.");return h;}
    private void openHistory(){try{History h=selectedHistory();Path dir=store.directory().resolve("history-files");Files.createDirectories(dir);Path path=dir.resolve("documento-"+h.id()+".docx");Files.write(path,h.docx());if(!Desktop.isDesktopSupported())throw new IllegalStateException("No hay aplicación asociada a DOCX en este equipo.");Desktop.getDesktop().open(path.toFile());saved("Abriendo DOCX conservado en el historial");}catch(Exception e){error(e);}}
    private void duplicateHistory(){try{History h=selectedHistory();data.saveHistory(new History(0,h.title()+" (copia)",h.templateId(),h.templateVersion(),null,h.valuesJson(),null,null,h.docx(),h.pdf()));refreshHistory();saved("Registro duplicado");}catch(Exception e){error(e);}}
    private void editHistory(){try{History h=selectedHistory();List<Kind> kinds=store.kinds();Kind k=kinds.stream().filter(x->x.templateId()==h.templateId()&&x.title().equals(h.title())).findFirst().or(()->kinds.stream().filter(x->x.templateId()==h.templateId()).findFirst()).orElseThrow(()->new IllegalStateException("El tipo documental ya no existe."));showGenerator();generateKind.getSelectionModel().select(generateKind.getItems().stream().filter(x->x.id()==k.id()).findFirst().orElse(k));buildDynamicForm();historicalTemplate=data.revision(h.templateId(),h.templateVersion());if(historicalTemplate==null)throw new IllegalStateException("No se conserva la versión de la plantilla.");var values=json.readTree(h.valuesJson());for(var entry:inputs.entrySet()){String value=values.path(entry.getKey()).asText("");if(entry.getValue() instanceof TextArea area)area.setText(value);else ((TextField)entry.getValue()).setText(value);}saved("Valores recuperados; edite y genere un nuevo documento");}catch(Exception e){error(e);}}
    private void exportHistory(boolean pdf){try{History h=selectedHistory();byte[] content=pdf?(h.pdf()==null?DocxPdfExporter.render(h.docx()):h.pdf()):h.docx();FileChooser fc=new FileChooser();fc.setInitialFileName(h.title().replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚ _-]","")+(pdf?".pdf":".docx"));fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(pdf?"PDF":"Word DOCX",pdf?"*.pdf":"*.docx"));File file=fc.showSaveDialog(stage);if(file==null)return;Files.write(file.toPath(),content);data.updateHistoryFiles(h.id(),pdf?null:file.toString(),pdf?file.toString():null,pdf?content:null);refreshHistory();saved("Archivo exportado nuevamente");}catch(Exception e){error(e);}}
    private void deleteHistory(){try{History h=selectedHistory();data.deleteHistory(h.id());refreshHistory();saved("Registro eliminado; los archivos exportados permanecen en su ubicación");}catch(Exception e){error(e);}}

    private void buildSettings(){settingsPage=new BorderPane();settingsPage.getStyleClass().add("page");VBox content=new VBox(14,new Label("TRANSFERENCIA ENTRE INSTALACIONES"),new Label("El paquete de configuración incluye plantillas, variables, tipos y correspondencias. Excluye credenciales y datos personales."),button("Exportar configuraciones",this::exportConfig),button("Importar configuraciones",this::importConfig),new Separator(),new Label("RESPALDO LOCAL COMPLETO"),new Label("Incluye plantillas, contactos e historial. Se cifra con una contraseña; el token Factiliza queda fuera."),button("Crear respaldo cifrado",this::backup),button("Restaurar respaldo cifrado",this::restore),new Label("Datos locales: "+store.directory()));content.getStyleClass().add("catalog-form");settingsPage.setCenter(content);}
    private void showSettings(){page("Configuración y respaldos",settingsPage);}
    private void exportConfig(){FileChooser fc=new FileChooser();fc.setInitialFileName("configuracion-documental.zip");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Paquete ZIP","*.zip"));File file=fc.showSaveDialog(stage);if(file==null)return;try{transfer.exportConfiguration(file.toPath());saved("Configuración exportada sin credenciales");}catch(Exception e){error(e);}}
    private void importConfig(){FileChooser fc=new FileChooser();fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Paquete ZIP","*.zip"));File file=fc.showOpenDialog(stage);if(file==null)return;ChoiceDialog<String> choice=new ChoiceDialog<>("Conservar existentes","Conservar existentes","Reemplazar coincidencias");choice.setTitle("Conflictos de importación");choice.setHeaderText("Si coinciden nombres de plantillas o títulos de documentos");String selected=choice.showAndWait().orElse(null);if(selected==null)return;try{StudioTransfer.ImportResult result=transfer.importConfiguration(file.toPath(),selected.startsWith("Conservar")?StudioTransfer.Conflict.KEEP:StudioTransfer.Conflict.REPLACE);refreshTemplates();refreshKinds();saved("Importadas "+result.templates()+" plantillas y "+result.kinds()+" tipos; coincidencias: "+result.conflicts());}catch(Exception e){error(e);}}
    private char[] password(String title){Dialog<String> dialog=new Dialog<>();dialog.setTitle(title);PasswordField field=new PasswordField();field.setPromptText("Contraseña de al menos 8 caracteres");dialog.getDialogPane().setContent(labelled("Contraseña",field));dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);dialog.setResultConverter(b->b==ButtonType.OK?field.getText():null);String value=dialog.showAndWait().orElse(null);return value==null?null:value.toCharArray();}
    private void backup(){char[] pass=password("Crear respaldo");if(pass==null)return;FileChooser fc=new FileChooser();fc.setInitialFileName("respaldo-documental.dsbk");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Respaldo cifrado","*.dsbk"));File file=fc.showSaveDialog(stage);if(file==null)return;try{transfer.backup(file.toPath(),pass);saved("Respaldo cifrado creado");}catch(Exception e){error(e);}}
    private void restore(){FileChooser fc=new FileChooser();fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Respaldo cifrado","*.dsbk"));File file=fc.showOpenDialog(stage);if(file==null)return;char[] pass=password("Restaurar respaldo");if(pass==null)return;Alert warning=new Alert(Alert.AlertType.CONFIRMATION,"La restauración reemplazará los datos locales actuales. ¿Continuar?",ButtonType.YES,ButtonType.NO);if(warning.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;try{transfer.restore(file.toPath(),pass);refreshTemplates();refreshKinds();refreshContacts();refreshHistory();saved("Respaldo restaurado; reinicie la aplicación para recargar todas las vistas");}catch(Exception e){error(e);}}
}
