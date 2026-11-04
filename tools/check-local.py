#!/usr/bin/env python3
"""Compile cached JVM dependencies without Gradle; does not replace platform builds."""
from pathlib import Path
import os, subprocess, sys
cache = Path(os.environ.get('GRADLE_CACHE', str(Path.home()/'.gradle/caches/modules-2/files-2.1')))
def jar(group, artifact, version):
    matches = list((cache/group/artifact/version).glob('*/*.jar'))
    return next(str(p) for p in matches if not any(s in p.name for s in ('-sources','-javadoc')))
def run(args, check=True):
    result = subprocess.run(args)
    if result.returncode: raise SystemExit(result.returncode)

compiler = [jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.4.21'),jar('org.jetbrains.kotlin','kotlin-stdlib','2.4.21'),jar('org.jetbrains.kotlin','kotlin-script-runtime','2.4.21'),jar('org.jetbrains.kotlin','kotlin-reflect','1.6.10'),jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.11.0'),jar('org.jetbrains','annotations','23.0.0')]
classpath = [compiler[1],compiler[-1],compiler[-2],jar('org.jetbrains.kotlinx','kotlinx-datetime-jvm','0.7.1'),jar('org.jetbrains.kotlin','kotlin-test','2.4.21'),jar('org.jetbrains.kotlin','kotlin-test-junit','2.4.21'),jar('junit','junit','4.13.2'),jar('org.hamcrest','hamcrest-core','1.3')]
output = Path('build/local-check');output.mkdir(parents=True,exist_ok=True)
classpath += [jar('org.jetbrains.kotlinx','kotlinx-serialization-json-jvm','1.8.1'), jar('org.jetbrains.kotlinx','kotlinx-serialization-core-jvm','1.8.1')]
sources = list(Path('domain/src').glob('**/*.kt'))
sources += [p for p in Path('data/src/commonMain/kotlin').glob('**/*.kt') if p.name != 'SqlLedgerRepository.kt']
sources += [p for p in Path('data/src/jvmTest').glob('**/*.kt') if p.name != 'SqlRepositoryTest.kt']
args = ['java','-cp',os.pathsep.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','11','-classpath',os.pathsep.join(classpath),'-d',str(output/'domain.jar'),*map(str,sources)]
run(args,check=True)
tests = ['com.maksym.financetracker.domain.'+p.stem for p in Path('domain/src/commonTest').glob('**/*Test.kt')]
tests += ['com.maksym.financetracker.domain.'+p.stem for p in Path('domain/src/jvmTest').glob('**/*Test.kt')]
tests += ['com.maksym.financetracker.data.'+p.stem for p in Path('data/src/jvmTest').glob('**/*Test.kt') if p.name != 'SqlRepositoryTest.kt']
if tests: run(['java','-cp',os.pathsep.join([str(output/'domain.jar'),*classpath]),'org.junit.runner.JUnitCore',*tests],check=True)
if '--ui' in sys.argv:
    ui_jars=[]
    for group in cache.iterdir():
        if group.name.startswith(('org.jetbrains.compose.', 'androidx.compose.', 'androidx.lifecycle', 'org.jetbrains.androidx.', 'androidx.savedstate', 'androidx.collection', 'androidx.annotation', 'androidx.navigationevent', 'org.jetbrains.skiko')):
            for artifact in group.iterdir():
                if group.name == "androidx.collection" and artifact.name == "collection": continue
                candidates=[p for p in artifact.glob('*/*/*.jar') if '-sources' not in p.name and '-javadoc' not in p.name and '-metadata' not in p.name]
                if candidates:
                    def version(p):
                        import re
                        return tuple(int(x) for x in re.findall(r'\d+',p.parent.parent.name))
                    ui_jars.append(str(max(candidates,key=version)))
    ui_sources=[Path('shared/src/commonMain/kotlin/com/maksym/financetracker/App.kt')]
    for part in ['ui','presentation']: ui_sources += list(Path('shared/src/commonMain/kotlin/com/maksym/financetracker',part).glob('*.kt'))
    cp=os.pathsep.join([str(output/'domain.jar'),*classpath,*ui_jars])
    run(['java','-Xmx2g','-cp',os.pathsep.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','11','-classpath',cp,'-Xplugin='+jar('org.jetbrains.kotlin','kotlin-compose-compiler-plugin-embeddable','2.4.21'),'-d',str(output/'shared.jar'),*map(str,ui_sources)],check=True)
    (output/'ui-classpath.txt').write_text(cp)
    presentation_tests=list(Path('shared/src/jvmTest/kotlin/com/maksym/financetracker/presentation').glob('*Test.kt'))
    if presentation_tests:
        testcp=os.pathsep.join([str(output/'shared.jar'),cp])
        run(['java','-cp',os.pathsep.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','11','-classpath',testcp,'-d',str(output/'presentation-tests.jar'),*map(str,presentation_tests)],check=True)
        run(['java','-cp',os.pathsep.join([str(output/'presentation-tests.jar'),testcp]),'org.junit.runner.JUnitCore',*['com.maksym.financetracker.presentation.'+p.stem for p in presentation_tests]],check=True)
    if '--screenshots' in sys.argv:
        rendercp=os.pathsep.join([str(output/'shared.jar'),cp,jar('org.jetbrains.kotlinx','kotlinx-coroutines-swing','1.11.0')])
        run(['java','-cp',os.pathsep.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','11','-classpath',rendercp,'-Xplugin='+jar('org.jetbrains.kotlin','kotlin-compose-compiler-plugin-embeddable','2.4.21'),'-d',str(output/'render.jar'),'tools/RenderScreenshots.kt'],check=True)
        import zipfile
        native = output/'native'; native.mkdir(exist_ok=True)
        with zipfile.ZipFile(jar('org.jetbrains.skiko','skiko-awt-runtime-linux-x64','0.150.2')) as archive:
            (native/'libskiko-linux-x64.so').write_bytes(archive.read('libskiko-linux-x64.so'))
        run(['java','-Dskiko.library.path='+str(native.absolute()),'-Djava.awt.headless=true','-cp',os.pathsep.join([str(output/'render.jar'),rendercp]),'RenderScreenshotsKt'],check=True)
