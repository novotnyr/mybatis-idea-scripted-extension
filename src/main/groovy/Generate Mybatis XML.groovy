import com.intellij.database.model.DasTable
import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.DasUtil
import com.intellij.openapi.project.Project

import com.intellij.database.view.generators.Files

/*
 * Available context bindings:
 *   SELECTION   Iterable<com.intellij.database.model.DasObject> -- selected entries in the tree
 *   PROJECT     com.intellij.openapi.project.Project -- currently opened project
 *   FILES       com.intellij.database.view.generators.Files -- files helper
 */


public class MybatisXmlMapperGenerator {
    public void start(Settings settings, Iterable<DasObject> selection, File targetDirectory) {
        selection
            .filter {
                it instanceof DasTable && it.getKind() == ObjectKind.TABLE
            }
            .find()
            .each { DasTable table ->
                GString mapperDefinition = emitMapper(table, settings)
                writeToDirectory(targetDirectory, mapperDefinition, table, settings)
            }
    }


    private void writeToDirectory(File directory, GString mapperDefinition, DasTable table, Settings settings) {
        String fileName = toEntityClassName(table, settings) + "Mapper.xml";
        new File(directory, fileName).write(mapperDefinition)
    }

    private GString emitMapper(DasTable table, Settings settings) {
        def namespace = settings.mapperNamespace + "." + toEntityClassName(table, settings) + "Mapper"

        GString mapperDefinition = emitBaseColumns(table, settings)
        mapperDefinition += emitResultMap(table, settings)
        mapperDefinition += emitFindById(table, settings)
        mapperDefinition += emitFindAll(table, settings)
        mapperDefinition += emitInsert(table, settings)


        """<?xml version="1.0" encoding="UTF-8" ?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
            <mapper namespace="${namespace}">
                ${mapperDefinition}
            </mapper>"""
    }

    private GString emitBaseColumns(DasTable table, Settings settings) {
        def columns = DasUtil.getColumns(table).collect { column ->
            def columnAlias
            if(settings.includeTableNameInColumnName) {
                columnAlias = table.name + "_" + column.name
            } else {
                columnAlias = column.name
            }

            "`${table.name}`.`${column.name}` AS `${columnAlias}`"
        }
        .join(',\n')

        def sqlId = getBaseColumnsSqlSnippetId(table)

        """
            <sql id="${sqlId}">
            ${columns}
            </sql>
        """
    }

    private GString emitFindAll(DasTable table, Settings settings) {
        def resultMap = getResultMapName(table)
        def refId = getBaseColumnsSqlSnippetId(table)

        """
            <select id="findAll" resultMap="${resultMap}">
                SELECT
                    <include refid="${refId}" />
                FROM ${table.name}
            </select>
        """
    }


    private GString emitFindById(DasTable table, Settings settings) {
        def resultMap = getResultMapName(table)
        def refId = getBaseColumnsSqlSnippetId(table)

        """
        <select id="findById" resultMap="${resultMap}">
            SELECT
                <include refid="${refId}" />
            FROM `${table.name}`
            WHERE `${table.name}`.`id`  = #{id}
        </select>
        """
    }

    private GString emitResultMap(DasTable table, Settings settings) {
        def resultType = settings.entityPackage + "." + toEntityClassName(table, settings)
        def resultMapId = getResultMapName(table)
        def mappings =  DasUtil
                .getColumns(table)
                .collect { column ->
            def propertyName = toPropertyName(column.name)

            def columnName
            if(settings.includeTableNameInColumnName) {
                columnName = table.name + "_" + column.name
            } else {
                columnName = column.name
            }

            if ( DasUtil.isPrimary(column)) {
                """<id column="${columnName}" property="${propertyName}" /> """
            } else {
                """<result column="${columnName}" property="${propertyName}" /> """
            }
        }
        .join('\n')

        """
            <resultMap id="${resultMapId}" type="${resultType}">
                ${mappings}
            </resultMap>
        """
    }

    private GString emitInsert(DasTable table, Settings settings) {
        def columnNames = DasUtil
                .getColumns(table)
                .collect { column -> column.name }
                .join(",\n")

        def columnValues = DasUtil
                .getColumns(table)
                .collect { column ->
            def propertyName = toPropertyName(column.name)
            def parameterObject = toStatementParameterName(table)
            "#{ ${parameterObject}.${propertyName} }"
        }
        .join(",\n")

        """
            <insert id="save">
                INSERT INTO `${table.name}`
                (
                    ${columnNames}
                )
                VALUES
                (
                    ${columnValues}
                )
            </insert>
        """
    }

    private CharSequence toCamelCase(CharSequence input) {
        return input
                .split('_')
                .collect{ it.capitalize() }
                .join('')
    }


    private CharSequence toPropertyName(CharSequence input) {
        def words = input
                .split('_')
                .collect { it.capitalize() }
        if (words.size() > 1) {
            return (words[0].toLowerCase() + words[1..-1].join("") )
        } else {
            return words[0].toLowerCase();
        }
    }

    private CharSequence toJavaClassName(DasTable table) {
        return toCamelCase(table.name)
    }

    private CharSequence toStatementParameterName(DasTable table) {
        return toPropertyName(table.name)
    }

    private CharSequence getBaseColumnsSqlSnippetId(DasTable table) {
        return toPropertyName(table.name) + "Columns";
    }

    private CharSequence getResultMapName(DasTable table) {
        return toPropertyName(table.name) + "ResultMap";
    }

    private CharSequence toEntityClassName(DasTable table, Settings settings) {
        if(settings.entityClassName == null || settings.entityClassName.isEmpty()) {
            return toJavaClassName(table)
        } else {
            return settings.entityClassName
        }
    }
}


class Settings {
    String mapperNamespace
    String entityPackage
    String entityClassName
    boolean includeTableNameInColumnName = true
}


// --- GUI
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import java.awt.GridLayout
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent
import com.intellij.openapi.application.ApplicationManager


class ExportSettingsDialog extends DialogWrapper {
    JLabel entityPackageLabel = new JLabel("Entity Package")

    JTextField entityPackageTextField = new JTextField();

    JLabel entityClassNameLabel = new JLabel("Entity Class Name");

    JTextField entityClassNameTextField = new JTextField()

    JLabel namespaceLabel = new JLabel("Mapper namespace")

    JTextField namespaceTextField = new JTextField()

    protected ExportSettingsDialog(Project project) {
        super(project)
        init()
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.add(this.entityPackageLabel)
        panel.add(this.entityPackageTextField)

        panel.add(this.entityClassNameLabel)
        panel.add(this.entityClassNameTextField)

        panel.add(this.namespaceLabel)
        panel.add(this.namespaceTextField)

        return panel;
    }
}


ApplicationManager.getApplication().invokeLater {
    def dialog = new ExportSettingsDialog(PROJECT)
    dialog.show()
    if (dialog.exitCode == DialogWrapper.OK_EXIT_CODE) {
        FILES.chooseDirectoryAndSave("Choose directory", "Choose the directory for generated files") { directory ->
            def settings = new Settings()
            settings.mapperNamespace = dialog.namespaceTextField.text
            settings.entityPackage = dialog.entityPackageTextField.text
            settings.entityClassName = dialog.entityClassNameTextField.text

            MybatisXmlMapperGenerator generator = new MybatisXmlMapperGenerator()
            generator.start(settings, SELECTION, directory)
        }
    }
}

