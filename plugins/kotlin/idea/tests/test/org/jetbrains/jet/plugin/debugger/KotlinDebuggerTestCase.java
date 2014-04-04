/*
 * Copyright 2010-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.plugin.debugger;

import com.intellij.debugger.DebuggerTestCase;
import com.intellij.debugger.impl.OutputChecker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.MockLibraryUtil;
import org.jetbrains.jet.asJava.KotlinLightClassForPackage;
import org.jetbrains.jet.codegen.forTestCompile.ForTestCompileRuntime;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.java.PackageClassUtils;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.plugin.PluginTestCaseBase;
import org.jetbrains.jet.plugin.framework.JavaRuntimeLibraryDescription;
import org.jetbrains.jet.testing.ConfigLibraryUtil;

import java.io.File;

public abstract class KotlinDebuggerTestCase extends DebuggerTestCase {
    protected static final String TINY_APP = PluginTestCaseBase.getTestDataPathBase() + "/debugger/tinyApp";

    private File outputDir;

    @Override
    protected OutputChecker initOutputChecker() {
        return new KotlinOutputChecker(TINY_APP);
    }

    @NotNull
    @Override
    protected String getTestAppPath() {
        return TINY_APP;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        UsefulTestCase.edt(new Runnable() {
            @Override
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();

                        NewLibraryEditor editor = new NewLibraryEditor();
                        editor.setName(JavaRuntimeLibraryDescription.LIBRARY_NAME);
                        editor.addRoot(VfsUtil.getUrlForLibraryRoot(ForTestCompileRuntime.runtimeJarForTests()), OrderRootType.CLASSES);

                        ConfigLibraryUtil.addLibrary(editor, model);

                        model.commit();
                    }
                });
            }
        });

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (outputDir != null && outputDir.exists()) {
            FileUtil.delete(outputDir);
        }
    }

    @Override
    protected void ensureCompiledAppExists() throws Exception {
        String modulePath = getTestAppPath();
        outputDir = new File(modulePath + File.separator + "classes");
        MockLibraryUtil.compileKotlin(modulePath + File.separator + "src", outputDir);
    }

    private static class KotlinOutputChecker extends OutputChecker {

        public KotlinOutputChecker(@NotNull String appPath) {
            super(appPath);
        }

        @Override
        protected String replaceAdditionalInOutput(String str) {
            return super.replaceAdditionalInOutput(str.replace(ForTestCompileRuntime.runtimeJarForTests().getPath(), "!KOTLIN_RUNTIME!"));
        }
    }

    @Override
    protected String getAppClassesPath() {
        return super.getAppClassesPath() + File.pathSeparator + ForTestCompileRuntime.runtimeJarForTests().getPath();
    }

    @Override
    protected void createBreakpoints(final String className) {
        PsiClass psiClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
            @Override
            public PsiClass compute() {
                return JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
            }
        });

        if (psiClass instanceof KotlinLightClassForPackage) {
            PsiElement element = psiClass.getNavigationElement();
            if (element instanceof JetFile) {
                createBreakpoints((JetFile) element);
                return;
            }
        }

        createBreakpoints(psiClass.getContainingFile());
    }

    @SuppressWarnings("MethodMayBeStatic")
    protected void createDebugProcess(@NotNull String path) throws Exception {
        File file = new File(path);
        String packageName = file.getName().replace(".kt", "");
        createLocalProcess(PackageClassUtils.getPackageClassFqName(new FqName(packageName)).asString());
    }

    @Override
    protected Sdk getTestProjectJdk() {
        return PluginTestCaseBase.jdkFromIdeaHome();
    }
}
