package jadx.plugins.mappings.save;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingUtil;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitOrder;
import net.fabricmc.mappingio.tree.VisitableMappingTree;

import jadx.api.data.ICodeComment;
import jadx.api.data.ICodeRename;
import jadx.api.data.IJavaNodeRef.RefType;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRef;
import jadx.api.metadata.annotations.VarNode;
import jadx.core.Consts;
import jadx.core.codegen.TypeGen;
import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.info.FieldInfo;
import jadx.core.dex.info.MethodInfo;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.RootNode;
import jadx.core.utils.files.FileUtils;
import jadx.plugins.mappings.RenameMappingsData;
import jadx.plugins.mappings.utils.DalvikToJavaBytecodeUtils;
import jadx.plugins.mappings.utils.VariablesUtils;

public class MappingExporter {
	private static final Logger LOG = LoggerFactory.getLogger(MappingExporter.class);

	private final RootNode root;
	private final @Nullable MappingTreeView loadedMappingTree;

	public MappingExporter(RootNode root) {
		this.root = root;
		this.loadedMappingTree = RenameMappingsData.getTree(this.root);
	}

	public void exportMappings(Path path, JadxCodeData codeData, MappingFormat mappingFormat) {
		VisitableMappingTree mappingTree = new MemoryMappingTree();
		// Map < SrcName >
		Set<String> mappedClasses = new HashSet<>();
		// Map < DeclClass + ShortId >
		Set<String> mappedFields = new HashSet<>();
		Set<String> mappedMethods = new HashSet<>();
		Set<String> methodsWithMappedElements = new HashSet<>();
		// Map < DeclClass + MethodShortId + CodeRef, NewName >
		Map<String, String> mappedMethodArgsAndVars = new HashMap<>();
		// Map < DeclClass [+ ShortId] [+ CodeRef], Comment >
		Map<String, String> comments = new HashMap<>();

		// We have to do this so we know for sure which elements are *manually* renamed
		for (ICodeRename codeRename : codeData.getRenames()) {
			if (codeRename.getNodeRef().getType().equals(RefType.CLASS)) {
				mappedClasses.add(codeRename.getNodeRef().getDeclaringClass());
			} else if (codeRename.getNodeRef().getType().equals(RefType.FIELD)) {
				mappedFields.add(codeRename.getNodeRef().getDeclaringClass() + codeRename.getNodeRef().getShortId());
			} else if (codeRename.getNodeRef().getType().equals(RefType.METHOD)) {
				if (codeRename.getCodeRef() == null) {
					mappedMethods.add(codeRename.getNodeRef().getDeclaringClass() + codeRename.getNodeRef().getShortId());
				} else {
					methodsWithMappedElements.add(codeRename.getNodeRef().getDeclaringClass() + codeRename.getNodeRef().getShortId());
					mappedMethodArgsAndVars.put(codeRename.getNodeRef().getDeclaringClass()
							+ codeRename.getNodeRef().getShortId()
							+ codeRename.getCodeRef(),
							codeRename.getNewName());
				}
			}
		}
		for (ICodeComment codeComment : codeData.getComments()) {
			comments.put(codeComment.getNodeRef().getDeclaringClass()
					+ (codeComment.getNodeRef().getShortId() == null ? "" : codeComment.getNodeRef().getShortId())
					+ (codeComment.getCodeRef() == null ? "" : codeComment.getCodeRef()),
					codeComment.getComment());
			if (codeComment.getCodeRef() != null) {
				methodsWithMappedElements.add(codeComment.getNodeRef().getDeclaringClass() + codeComment.getNodeRef().getShortId());
			}
		}

		try {
			String srcNamespace = MappingUtil.NS_SOURCE_FALLBACK;
			String dstNamespace = MappingUtil.NS_TARGET_FALLBACK;

			// Copy mappings from potentially imported mappings file
			if (loadedMappingTree != null && loadedMappingTree.getDstNamespaces() != null) {
				loadedMappingTree.accept(mappingTree);
			}

			mappingTree.visitHeader();
			mappingTree.visitNamespaces(srcNamespace, Collections.singletonList(dstNamespace));
			mappingTree.visitContent();

			for (ClassNode cls : root.getClasses()) {
				ClassInfo classInfo = cls.getClassInfo();
				String classPath = classInfo.makeRawFullName().replace('.', '/');
				String rawClassName = classInfo.getRawName();

				if (classInfo.hasAlias()
						&& !classInfo.getAliasShortName().equals(classInfo.getShortName())
						&& mappedClasses.contains(rawClassName)) {
					mappingTree.visitClass(classPath);
					String alias = classInfo.makeAliasRawFullName().replace('.', '/');

					if (alias.startsWith(Consts.DEFAULT_PACKAGE_NAME)) {
						alias = alias.substring(Consts.DEFAULT_PACKAGE_NAME.length() + 1);
					}
					mappingTree.visitDstName(MappedElementKind.CLASS, 0, alias);
				}
				if (comments.containsKey(rawClassName)) {
					mappingTree.visitClass(classPath);
					mappingTree.visitComment(MappedElementKind.CLASS, comments.get(rawClassName));
				}

				for (FieldNode fld : cls.getFields()) {
					FieldInfo fieldInfo = fld.getFieldInfo();
					if (fieldInfo.hasAlias() && mappedFields.contains(rawClassName + fieldInfo.getShortId())) {
						visitField(mappingTree, classPath, fieldInfo.getName(), TypeGen.signature(fieldInfo.getType()));
						mappingTree.visitDstName(MappedElementKind.FIELD, 0, fieldInfo.getAlias());
					}
					if (comments.containsKey(rawClassName + fieldInfo.getShortId())) {
						visitField(mappingTree, classPath, fieldInfo.getName(), TypeGen.signature(fieldInfo.getType()));
						mappingTree.visitComment(MappedElementKind.FIELD, comments.get(rawClassName + fieldInfo.getShortId()));
					}
				}

				for (MethodNode mth : cls.getMethods()) {
					MethodInfo methodInfo = mth.getMethodInfo();
					String methodName = methodInfo.getName();
					String methodDesc = methodInfo.getShortId().substring(methodName.length());
					if (methodInfo.hasAlias() && mappedMethods.contains(rawClassName + methodInfo.getShortId())) {
						visitMethod(mappingTree, classPath, methodName, methodDesc);
						mappingTree.visitDstName(MappedElementKind.METHOD, 0, methodInfo.getAlias());
					}
					if (comments.containsKey(rawClassName + methodInfo.getShortId())) {
						visitMethod(mappingTree, classPath, methodName, methodDesc);
						mappingTree.visitComment(MappedElementKind.METHOD, comments.get(rawClassName + methodInfo.getShortId()));
					}

					if (!methodsWithMappedElements.contains(rawClassName + methodInfo.getShortId())) {
						continue;
					}
					// Method args
					int lvtIndex = mth.getAccessFlags().isStatic() ? 0 : 1;
					List<VarNode> args = mth.collectArgNodes();
					for (VarNode arg : args) {
						Integer lvIndex = DalvikToJavaBytecodeUtils.getMethodArgLvIndex(arg);
						if (lvIndex == null) {
							lvIndex = -1;
						}
						String key = rawClassName + methodInfo.getShortId()
								+ JadxCodeRef.forVar(arg.getReg(), arg.getSsa());
						if (mappedMethodArgsAndVars.containsKey(key)) {
							visitMethodArg(mappingTree, classPath, methodName, methodDesc, args.indexOf(arg), lvIndex);
							mappingTree.visitDstName(MappedElementKind.METHOD_ARG, 0, mappedMethodArgsAndVars.get(key));
							mappedMethodArgsAndVars.remove(key);
						}
						lvtIndex++;
						// Not checking for comments since method args can't have any
					}
					// Method vars
					for (VariablesUtils.VarInfo info : VariablesUtils.collect(mth)) {
						VarNode var = info.getVar();
						int startOpIdx = info.getStartOpIdx();
						int endOpIdx = info.getEndOpIdx();
						int lvIndex = DalvikToJavaBytecodeUtils.getMethodVarLvIndex(var);
						String key = rawClassName + methodInfo.getShortId()
								+ JadxCodeRef.forVar(var.getReg(), var.getSsa());
						if (mappedMethodArgsAndVars.containsKey(key)) {
							visitMethodVar(mappingTree, classPath, methodName, methodDesc, lvtIndex, lvIndex, startOpIdx, endOpIdx);
							mappingTree.visitDstName(MappedElementKind.METHOD_VAR, 0, mappedMethodArgsAndVars.get(key));
						}
						key = rawClassName + methodInfo.getShortId() + JadxCodeRef.forInsn(startOpIdx);
						if (comments.containsKey(key)) {
							visitMethodVar(mappingTree, classPath, methodName, methodDesc, lvtIndex, lvIndex, startOpIdx, endOpIdx);
							mappingTree.visitComment(MappedElementKind.METHOD_VAR, comments.get(key));
						}
						lvtIndex++;
					}
				}
			}
			// write file as late as possible because a mapping collection can fail with exception
			if (mappingFormat.hasSingleFile()) {
				FileUtils.deleteFileIfExists(path);
				FileUtils.makeDirsForFile(path);
				Files.createFile(path);
			} else {
				FileUtils.makeDirs(path);
			}
			// Write file
			mappingTree.accept(MappingWriter.create(path, mappingFormat), VisitOrder.createByName());
			mappingTree.visitEnd();
		} catch (Exception e) {
			LOG.error("Failed to save deobfuscation map file '{}'", path.toAbsolutePath(), e);
		}
	}

	private void visitField(VisitableMappingTree tree, String classPath, String srcName, String srcDesc) throws IOException {
		tree.visitClass(classPath);
		tree.visitField(srcName, srcDesc);
	}

	private void visitMethod(VisitableMappingTree tree, String classPath, String srcName, String srcDesc) throws IOException {
		tree.visitClass(classPath);
		tree.visitMethod(srcName, srcDesc);
	}

	private void visitMethodArg(VisitableMappingTree tree, String classPath, String methodSrcName, String methodSrcDesc, int argPosition,
			int lvIndex) throws IOException {
		visitMethod(tree, classPath, methodSrcName, methodSrcDesc);
		tree.visitMethodArg(argPosition, lvIndex, null);
	}

	private void visitMethodVar(VisitableMappingTree tree, String classPath, String methodSrcName, String methodSrcDesc, int lvtIndex,
			int lvIndex, int startOpIdx, int endOpIdx) throws IOException {
		visitMethod(tree, classPath, methodSrcName, methodSrcDesc);
		tree.visitMethodVar(lvtIndex, lvIndex, startOpIdx, endOpIdx, null);
	}
}
