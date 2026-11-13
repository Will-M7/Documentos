package com.ejemplo.crudpersona.studio;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.ejemplo.crudpersona.studio.DocumentStore.*;
import com.ejemplo.crudpersona.studio.StudioData.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateCatalogUiTest {
    @TempDir Path temp;

    @BeforeAll static void startToolkit()throws Exception{
        if(System.getProperty("javafx.cachedir")==null)System.setProperty("javafx.cachedir",Path.of(System.getProperty("java.io.tmpdir"),"document-studio-javafx-tests").toString());
        Platform.startup(()->{Platform.setImplicitExit(false);Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());});
    }
    @AfterAll static void stopToolkit(){Platform.exit();}

    @Test void libraryAlwaysShowsAllTemplatesAndHighlightsOnlyMembersOfTheSelectedRealGroup()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template first=template(store,"Solicitud","Plantilla"),second=template(store,"Carta","Plantilla"),unassigned=template(store,"Libre","Plantilla");
        TemplateGroup requests=store.saveGroup(0,"Solicitudes"),letters=store.saveGroup(0,"Cartas");store.setTemplateGroups(first.id(),List.of(requests.id()));store.setTemplateGroups(second.id(),List.of(letters.id()));
        fx(()->{
            Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store);stage.setScene(scene(window));
            try{
                invoke(window,"showLibrary");ListView<TemplateGroup> groups=value(window,"groupList");ListView<Template> catalog=value(window,"libraryList");
                assertThat(groups.getItems()).containsExactlyElementsOf(store.groups());assertThat(catalog.getItems()).extracting(Template::id).containsExactlyInAnyOrder(first.id(),second.id(),unassigned.id());
                groups.getSelectionModel().select(groups.getItems().stream().filter(g->g.id()==requests.id()).findFirst().orElseThrow());
                assertThat(catalog.getItems()).hasSize(3);ListCell<Template> cell=catalog.getCellFactory().call(catalog);updateCell(cell,first);assertThat(cell.getStyleClass()).contains("library-associated");updateCell(cell,second);assertThat(cell.getStyleClass()).doesNotContain("library-associated");
                groups.getSelectionModel().select(groups.getItems().stream().filter(g->g.id()==letters.id()).findFirst().orElseThrow());
                assertThat(catalog.getItems()).hasSize(3);updateCell(cell,first);assertThat(cell.getStyleClass()).doesNotContain("library-associated");updateCell(cell,second);assertThat(cell.getStyleClass()).contains("library-associated");
                TextField search=value(window,"librarySearch");search.setText("sol");assertThat(catalog.getItems()).extracting(Template::id).containsExactly(first.id());search.clear();assertThat(catalog.getItems()).hasSize(3);
                for(TemplateGroup group:store.groups())store.deleteGroup(group.id(),null);invoke(window,"refreshLibrary");assertThat(groups.getItems()).isEmpty();assertThat(catalog.getItems()).hasSize(3);updateCell(cell,second);assertThat(cell.getStyleClass()).doesNotContain("library-associated");
                window.root().applyCss();window.root().layout();BorderPane page=value(window,"libraryPage");assertThat(buttonLabels(page)).containsExactlyInAnyOrder("Nuevo grupo","Renombrar grupo","Eliminar grupo","Abrir en editor","Renombrar plantilla","Duplicar plantilla","Eliminar plantilla","Añadir plantilla al grupo","Quitar del grupo");
            }finally{stage.close();}return null;
        });
    }

    @Test void generatorContainsOnlyRealGroupsAndTheirRealTemplatesAndBuildsActualMarkers()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template first=template(store,"Carta","{{cliente_dni}} {{nombre}}"),second=template(store,"Contrato","Otro"),unassigned=template(store,"Sin asignar","Otro");
        TemplateGroup a=store.saveGroup(0,"A"),b=store.saveGroup(0,"B");store.setTemplateGroups(first.id(),List.of(a.id()));store.setTemplateGroups(second.id(),List.of(b.id()));
        store.saveVariable(new Variable(0,first.id(),"nombre","Nombre","Texto corto","",false,1));store.saveDefinition(new Definition(0,"cliente_dni","DNI del cliente","Texto corto",""));
        store.saveKind(new Kind(0,"Registro antiguo","","",first.id(),"campo_antiguo",true));
        fx(()->{
            Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store);stage.setScene(scene(window));
            try{
                invoke(window,"showGenerator");ComboBox<TemplateGroup> groups=value(window,"generateGroup");ComboBox<Template> choices=value(window,"generateTemplate");assertThat(groups.getItems()).containsExactlyElementsOf(store.groups());assertThat(choices.getItems()).extracting(Template::id).containsExactly(first.id());
                choices.getSelectionModel().selectFirst();invoke(window,"buildDynamicForm");Map<String,Control> fields=value(window,"inputs");assertThat(fields.keySet()).containsExactlyInAnyOrder("nombre","cliente_dni");window.root().applyCss();window.root().layout();assertThat(buttonLabels((BorderPane)value(window,"generatorPage"))).contains("Consultar API","Abrir en Word","Imprimir");
                groups.getSelectionModel().select(groups.getItems().stream().filter(g->g.id()==b.id()).findFirst().orElseThrow());assertThat(choices.getItems()).extracting(Template::id).containsExactly(second.id());
                TextField search=value(window,"generateSearch");search.setText("carta");assertThat(choices.getItems()).isEmpty();search.clear();groups.getSelectionModel().selectFirst();search.setText("CaRtA");assertThat(choices.getItems()).extracting(Template::id).containsExactly(first.id());assertThat(choices.getItems()).extracting(Template::id).doesNotContain(unassigned.id());
            }finally{stage.close();}return null;
        });
    }

    @Test void settingsLayoutKeepsExactlyThreeScopesAndButtonStylesInBothThemes()throws Exception{
        DocumentStore store=new DocumentStore(temp);template(store,"Carta","Contenido");store.saveGroup(0,"Cartas");
        fx(()->{
            Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store);Scene scene=scene(window);stage.setScene(scene);
            try{
                invoke(window,"showSettings");ComboBox<String> scope=value(window,"exportScope");ComboBox<Template> templates=value(window,"exportTemplate");ComboBox<TemplateGroup> groups=value(window,"exportGroup");
                assertThat(scope.getItems()).containsExactly("Solo una plantilla","Todas las plantillas de un grupo","Todas las plantillas");assertThat(templates.isDisabled()).isFalse();assertThat(groups.isDisabled()).isTrue();
                scope.getSelectionModel().select(1);assertThat(templates.isDisabled()).isTrue();assertThat(groups.isDisabled()).isFalse();scope.getSelectionModel().select(2);assertThat(templates.isDisabled()).isTrue();assertThat(groups.isDisabled()).isTrue();
                ComboBox<String> conflicts=value(window,"importConflict");assertThat(conflicts.getItems()).containsExactly("Conservar existentes","Reemplazar existentes");
                for(boolean dark:List.of(false,true)){
                    Application.setUserAgentStylesheet(dark?new PrimerDark().getUserAgentStylesheet():new PrimerLight().getUserAgentStylesheet());window.root().applyCss();window.root().layout();
                    assertThat(window.root().lookupAll(".transfer-card")).hasSize(3);
                    Button importing=window.root().lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("Importar archivo (.zip)")).map(n->(Button)n).findFirst().orElseThrow();
                    assertThat(importing.getStyleClass()).contains("button","primary-btn");assertThat(importing.getMinHeight()).isGreaterThanOrEqualTo(36);assertThat(importing.getBackground()).isNotNull();
                    VBox sidebar=value(window,"sidebar");assertThat(sidebar.getPrefWidth()).isEqualTo(242);
                    for(String view:List.of("showGenerator","showDefinitions","showLibrary","showEditor","showContacts","showHistory","showApi","showSettings")){
                        invoke(window,view);window.root().applyCss();window.root().layout();
                        assertThat(((Label)value(window,"status")).getText()).doesNotContain(store.directory().toString());
                        snapshot(window,view+(dark?"-dark":"-light"));
                    }
                }
            }finally{stage.close();}
            return null;
        });
    }

    @Test void generationUsesLocalExtrasThenSavesManualChangesAndApiResultsWithoutConfirmation()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);ApiCredentialStore vault=new ApiCredentialStore(temp);vault.add("Prueba","Factiliza","fixture-token");
        Template template=template(store,"Carta","{{dni}} {{nombre}} {{direccion}} {{ubigeo}}");
        TemplateGroup group=store.saveGroup(0,"Cartas");store.setTemplateGroups(template.id(),List.of(group.id()));
        List<Variable> variables=new ArrayList<>();int position=1;
        for(String key:List.of("dni","nombre","direccion","ubigeo"))variables.add(store.saveVariable(new Variable(0,template.id(),key,key,switch(key){case "dni"->"DNI";case "direccion"->"Domicilio";default->"Texto corto";},"",false,position++)));
        for(Variable v:variables)data.saveApiBinding(new ApiBinding(store.definitionId(v.id()),"DNI","field:"+switch(v.key()){case "dni"->"numero";case "nombre"->"nombres";case "direccion"->"direccion";default->"ubigeo";}));
        data.saveContact(new Contact(0,"Persona","Ana","Pérez","DNI","12345678","Lima","","{\"ubigeo\":\"150101\",\"data.extra\":\"valor\"}"));
        AtomicInteger calls=new AtomicInteger();AtomicReference<Map<String,String>> response=new AtomicReference<>(Map.of("data.numero","87654321","data.nombres","María","data.apellido_paterno","Ruiz","data.direccion","Cusco","data.ubigeo[2]","080101","data.extra","conservado"));
        LookupProvider provider=new LookupProvider(){public String name(){return "Factiliza";}public Set<String> services(){return Set.of("DNI","RUC");}public Map<String,String> query(String service,String identifier,String token){calls.incrementAndGet();return response.get();}};
        LookupRouter router=new LookupRouter(vault,List.of(provider));
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store,router);stage.setScene(scene(created));return created;});
        try{
            fx(()->{
                invoke(window,"showGenerator");ComboBox<Template> templates=value(window,"generateTemplate");templates.getSelectionModel().selectFirst();invoke(window,"buildDynamicForm");Template selected=templates.getValue();Map<String,Control> fields=value(window,"inputs");
                TextField dni=(TextField)fields.get("dni");dni.setText("12345678");
                invoke(window,"lookupForField",new Class<?>[]{Template.class,Variable.class,TextField.class,String.class},selected,variables.get(0),dni,"DNI");
                assertThat(calls).hasValue(0);assertThat(((TextField)fields.get("nombre")).getText()).isEqualTo("Ana");assertThat(((TextArea)fields.get("direccion")).getText()).isEqualTo("Lima");assertThat(((TextField)fields.get("ubigeo")).getText()).isEqualTo("150101");assertThat(((Label)value(window,"status")).getText()).isEqualTo("Datos cargados desde Clientes locales");
                ((TextField)fields.get("nombre")).setText("Ana actualizada");((TextArea)fields.get("direccion")).setText("Arequipa");invoke(window,"makePreview");assertThat((byte[])value(window,"generatedBytes")).isNotEmpty();
                Contact saved=data.matchingContact("Persona","DNI","12345678");assertThat(saved.given()).isEqualTo("Ana actualizada");assertThat(saved.address()).isEqualTo("Arequipa");assertThat(saved.extrasJson()).contains("150101","data.extra");window.root().applyCss();window.root().layout();snapshot(window,"generator-with-data");return null;
            });
            CompletableFuture<LookupRouter.Result> queried=new CompletableFuture<>();fx(()->{invoke(window,"runApiQuery",new Class<?>[]{String.class,String.class,Long.class,Consumer.class},"DNI","87654321",null,(Consumer<LookupRouter.Result>)r->{try{invoke(window,"showApiResult",new Class<?>[]{LookupRouter.Result.class},r);queried.complete(r);}catch(Exception e){queried.completeExceptionally(e);}});return null;});queried.get(20,TimeUnit.SECONDS);
            Contact saved=data.matchingContact("Persona","DNI","87654321");assertThat(saved.given()).isEqualTo("María");assertThat(saved.family()).isEqualTo("Ruiz");assertThat(saved.extrasJson()).contains("data.extra","conservado","080101");
            fx(()->{assertThat(((Label)value(window,"apiSaved")).getText()).isEqualTo("✓ Guardado automáticamente en Clientes");return null;});assertThat(data.contacts()).hasSize(2);
        }finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void openingAndPrintingSaveNamedIdentifierFormsAndAllExtrasBeforeDispatch()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);Template template=template(store,"Documento","{{documento}} {{cliente}} {{apellidos}} {{direccion}} {{referencia}}");TemplateGroup group=store.saveGroup(0,"Documentos");store.setTemplateGroups(template.id(),List.of(group.id()));
        for(String key:List.of("documento","cliente","apellidos","direccion","referencia"))store.saveVariable(new Variable(0,template.id(),key,key.equals("documento")?"DNI del cliente":key,"Texto corto","",false,1));
        List<Path> opened=new ArrayList<>(),printed=new ArrayList<>();StudioWindow.DocumentActions actions=new StudioWindow.DocumentActions(){
            public void open(Path file)throws Exception{assertThat(data.matchingContact("Persona","DNI","12345678").given()).isEqualTo("Ana");opened.add(file);}
            public void print(Path file)throws Exception{assertThat(data.matchingContact("Persona","DNI","12345678").given()).isEqualTo("Ana nueva");printed.add(file);}
        };
        fx(()->{
            Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store,null,actions);stage.setScene(scene(window));
            try{
                invoke(window,"showGenerator");ComboBox<Template> choices=value(window,"generateTemplate");choices.getSelectionModel().selectFirst();invoke(window,"buildDynamicForm");Map<String,Control> fields=value(window,"inputs");
                ((TextField)fields.get("documento")).setText("12345678");((TextField)fields.get("cliente")).setText("Ana");((TextField)fields.get("apellidos")).setText("Pérez");((TextField)fields.get("direccion")).setText("Lima");((TextField)fields.get("referencia")).setText("Puerta azul");
                invoke(window,"openGeneratedInWord");assertThat(opened).hasSize(1);assertThat(opened.get(0).getFileName().toString()).startsWith("documento-").endsWith(".docx");try(XWPFDocument doc=DocumentEngine.open(Files.readAllBytes(opened.get(0)))){assertThat(doc.getParagraphs().get(0).getText()).contains("Ana","Puerta azul");}
                ((TextField)fields.get("cliente")).setText("Ana nueva");((TextField)fields.get("referencia")).setText("Puerta verde");invoke(window,"printGenerated");assertThat(printed).hasSize(1);assertThat(Files.readAllBytes(printed.get(0))).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                assertThat(data.contacts()).hasSize(1);Contact contact=data.matchingContact("Persona","DNI","12345678");assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(contact.extrasJson()).path("referencia").asText()).isEqualTo("Puerta verde");
                ((TextField)fields.get("documento")).clear();((TextField)fields.get("cliente")).clear();((TextField)fields.get("referencia")).clear();((TextField)fields.get("documento")).setText("12345678");assertThat(((TextField)fields.get("cliente")).getText()).isEqualTo("Ana nueva");assertThat(((TextField)fields.get("referencia")).getText()).isEqualTo("Puerta verde");
                invoke(window,"showContacts");window.root().applyCss();window.root().layout();assertThat(buttonLabels((BorderPane)value(window,"contactsPage"))).containsExactlyInAnyOrder("Guardar cliente","Eliminar cliente");ListView<Contact> clients=value(window,"contactList");assertThat(clients.getItems()).hasSize(1);assertThat(clients.getSelectionModel().getSelectedItem()).isNotNull();
                ((TextField)value(window,"contactGiven")).setText("Editada en Clientes");invoke(window,"saveContact");assertThat(data.contacts()).hasSize(1);assertThat(data.matchingContact("Persona","DNI","12345678").given()).isEqualTo("Editada en Clientes");
                TextField search=value(window,"contactSearch");search.setText("inexistente");assertThat(clients.getItems()).isEmpty();search.clear();assertThat(clients.getItems()).hasSize(1);invoke(window,"deleteContact");assertThat(data.contacts()).isEmpty();
            }finally{stage.close();for(Path file:opened)Files.deleteIfExists(file);for(Path file:printed)Files.deleteIfExists(file);}return null;
        });
    }

    @Test void enteringCompleteRucAutomaticallyQueriesOnlyOnceThenReusesLocalData()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);Template template=template(store,"Empresa","{{ruc_empresa}} {{razon_social}} {{direccion}}");TemplateGroup group=store.saveGroup(0,"Empresas");store.setTemplateGroups(template.id(),List.of(group.id()));
        for(String key:List.of("ruc_empresa","razon_social","direccion"))store.saveVariable(new Variable(0,template.id(),key,key,"Texto corto","",false,1));
        ApiCredentialStore vault=new ApiCredentialStore(temp);vault.add("Prueba","Factiliza","fixture-token");AtomicInteger calls=new AtomicInteger();LookupProvider provider=new LookupProvider(){public String name(){return "Factiliza";}public Set<String> services(){return Set.of("RUC");}public Map<String,String> query(String service,String number,String credential){calls.incrementAndGet();return Map.of("data.numero",number,"data.nombre_o_razon_social","ACME","data.direccion","Cusco");}};
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store,new LookupRouter(vault,List.of(provider)));stage.setScene(scene(created));return created;});
        CompletableFuture<Void> completed=new CompletableFuture<>();
        try{
            fx(()->{invoke(window,"showGenerator");((ComboBox<Template>)value(window,"generateTemplate")).getSelectionModel().selectFirst();invoke(window,"buildDynamicForm");Map<String,Control> fields=value(window,"inputs");((TextField)fields.get("razon_social")).textProperty().addListener((o,a,b)->{if(b.equals("ACME"))completed.complete(null);});((TextField)fields.get("razon_social")).setText("Dato anterior");((TextField)fields.get("ruc_empresa")).setText("20123456789");return null;});completed.get(20,TimeUnit.SECONDS);
            fx(()->{assertThat(calls).hasValue(1);assertThat(data.matchingContact("Empresa","RUC","20123456789").given()).isEqualTo("ACME");Map<String,Control> fields=value(window,"inputs");assertThat(((TextField)fields.get("direccion")).getText()).isEqualTo("Cusco");((TextField)fields.get("ruc_empresa")).clear();((TextField)fields.get("razon_social")).clear();((TextField)fields.get("ruc_empresa")).setText("20123456789");assertThat(((TextField)fields.get("razon_social")).getText()).isEqualTo("ACME");assertThat(((Label)value(window,"status")).getText()).isEqualTo("Datos cargados desde Clientes locales");assertThat(calls).hasValue(1);return null;});
        }finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    private static List<String> buttonLabels(javafx.scene.Parent page){return page.lookupAll(".button").stream().filter(n->n instanceof Button).map(n->((Button)n).getText()).toList();}
    private static void updateCell(ListCell<Template> cell,Template value)throws Exception{Method method=cell.getClass().getDeclaredMethod("updateItem",Template.class,boolean.class);method.setAccessible(true);method.invoke(cell,value,false);}

    private static Scene scene(StudioWindow window){Scene scene=new Scene(window.root(),1280,800);for(String file:List.of("/css/app.css","/css/studio.css"))scene.getStylesheets().add(TemplateCatalogUiTest.class.getResource(file).toExternalForm());return scene;}
    private static Template template(DocumentStore store,String name,String text)throws Exception{try(XWPFDocument document=new XWPFDocument()){document.createParagraph().createRun().setText(text);byte[] bytes=DocumentEngine.bytes(document);return store.saveTemplate(new Template(0,name,bytes,bytes));}}
    private static void snapshot(StudioWindow window,String name)throws Exception{
        String directory=System.getProperty("studio.ui.snapshots");if(directory==null)return;
        javafx.scene.image.WritableImage pixels=window.root().snapshot(null,null);java.awt.image.BufferedImage image=new java.awt.image.BufferedImage((int)pixels.getWidth(),(int)pixels.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,pixels.getPixelReader().getArgb(x,y));Path path=Path.of(directory);Files.createDirectories(path);javax.imageio.ImageIO.write(image,"png",path.resolve(name+".png").toFile());
    }
    private static <T> T fx(Callable<T> action)throws Exception{CompletableFuture<T> result=new CompletableFuture<>();Platform.runLater(()->{try{result.complete(action.call());}catch(Throwable e){result.completeExceptionally(e);}});return result.get(30,TimeUnit.SECONDS);}
    private static Object invoke(Object target,String name)throws Exception{return invoke(target,name,new Class<?>[0]);}
    private static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    @SuppressWarnings("unchecked") private static <T> T value(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return (T)field.get(target);}
}
