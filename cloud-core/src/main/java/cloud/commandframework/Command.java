//
// MIT License
//
// Copyright (c) 2022 Alexander Söderberg & Contributors
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package cloud.commandframework;

import cloud.commandframework.arguments.DefaultValue;
import cloud.commandframework.arguments.LiteralParser;
import cloud.commandframework.arguments.PreprocessorHolder;
import cloud.commandframework.arguments.compound.ArgumentPair;
import cloud.commandframework.arguments.compound.ArgumentTriplet;
import cloud.commandframework.arguments.flags.CommandFlag;
import cloud.commandframework.arguments.flags.CommandFlagParser;
import cloud.commandframework.arguments.parser.ParserDescriptor;
import cloud.commandframework.arguments.suggestion.SuggestionProvider;
import cloud.commandframework.execution.CommandExecutionHandler;
import cloud.commandframework.keys.CloudKey;
import cloud.commandframework.keys.CloudKeyHolder;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.meta.SimpleCommandMeta;
import cloud.commandframework.permission.CommandPermission;
import cloud.commandframework.permission.Permission;
import cloud.commandframework.permission.PredicatePermission;
import cloud.commandframework.types.tuples.Pair;
import cloud.commandframework.types.tuples.Triplet;
import io.leangen.geantyref.TypeToken;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A command is a chain of {@link CommandComponent command components} with an associated {@link #commandExecutionHandler()}.
 * <p>
 * The recommended way of creating a command is by using a {@link Command.Builder command builder}.
 * You may either create the command builder using {@link #newBuilder(String, CommandMeta, String...)} or
 * {@link CommandManager#commandBuilder(String, String...)}.
 * Getting a builder from the command manager means that the builder is linked to the manager.
 * When the command builder is linked to the manager, it is able to retrieve parsers from the associated
 * {@link cloud.commandframework.arguments.parser.ParserRegistry} in the case that only a parsed type is given to the builder,
 * and not a complete parser.
 * You may link any command builder to a command manager by using {@link Command.Builder#manager(CommandManager)}.
 * <p>
 * All command flags added to a command builder will be collected into a single component.
 * If there are flags added to the command, then they may be retrieved from the {@link #flagComponent()} or from the
 * {@link #flagParser()}.
 * <p>
 * Commands may have meta-data associated with them, which can be accessed using {@link #commandMeta()}.
 * A common way of using the command meta is by using it to filter out commands in post-processing.
 * <p>
 * A command may have a {@link #senderType()} that is different from the sender type of the command manager.
 * The command tree will enforce this type when parsing the command.
 *
 * @param <C> Command sender type
 */
@SuppressWarnings("unused")
@API(status = API.Status.STABLE)
public class Command<C> {

    private final List<@NonNull CommandComponent<C>> components;
    private final @Nullable CommandComponent<C> flagComponent;
    private final CommandExecutionHandler<C> commandExecutionHandler;
    private final Class<? extends C> senderType;
    private final CommandPermission commandPermission;
    private final CommandMeta commandMeta;

    /**
     * Constructs a new command.
     *
     * @param commandComponents       command component argument and description
     * @param commandExecutionHandler execution handler
     * @param senderType              required sender type. May be {@code null}
     * @param commandPermission       command permission
     * @param commandMeta             command meta instance
     * @since 1.3.0
     */
    @API(status = API.Status.STABLE, since = "1.3.0")
    public Command(
            final @NonNull List<@NonNull CommandComponent<C>> commandComponents,
            final @NonNull CommandExecutionHandler<@NonNull C> commandExecutionHandler,
            final @Nullable Class<? extends C> senderType,
            final @NonNull CommandPermission commandPermission,
            final @NonNull CommandMeta commandMeta
    ) {
        this.components = Objects.requireNonNull(commandComponents, "Command components may not be null");
        if (this.components.isEmpty()) {
            throw new IllegalArgumentException("At least one command component is required");
        }

        this.flagComponent =
                this.components.stream()
                        .filter(ca -> ca.type() == CommandComponent.ComponentType.FLAG)
                        .findFirst()
                        .orElse(null);

        // Enforce ordering of command arguments
        boolean foundOptional = false;
        for (final CommandComponent<C> component : this.components) {
            if (component.name().isEmpty()) {
                throw new IllegalArgumentException("Component names may not be empty");
            }
            if (foundOptional && component.required()) {
                throw new IllegalArgumentException(
                        String.format(
                                "Command component '%s' cannot be placed after an optional argument",
                                component.name()
                        ));
            } else if (!component.required()) {
                foundOptional = true;
            }
        }
        this.commandExecutionHandler = commandExecutionHandler;
        this.senderType = senderType;
        this.commandPermission = commandPermission;
        this.commandMeta = commandMeta;
    }

    /**
     * Constructs a new command.
     *
     * @param commandComponents       command components
     * @param commandExecutionHandler execution handler
     * @param senderType              required sender type. May be {@code null}
     * @param commandMeta             command meta instance
     * @since 1.3.0
     */
    @API(status = API.Status.STABLE, since = "1.3.0")
    public Command(
            final @NonNull List<@NonNull CommandComponent<C>> commandComponents,
            final @NonNull CommandExecutionHandler<@NonNull C> commandExecutionHandler,
            final @Nullable Class<? extends C> senderType,
            final @NonNull CommandMeta commandMeta
    ) {
        this(commandComponents, commandExecutionHandler, senderType, Permission.empty(), commandMeta);
    }

    /**
     * Constructs a new command.
     *
     * @param commandComponents       command components
     * @param commandExecutionHandler execution handler
     * @param commandPermission       command permission
     * @param commandMeta             command meta instance
     * @since 1.3.0
     */
    @API(status = API.Status.STABLE, since = "1.3.0")
    public Command(
            final @NonNull List<@NonNull CommandComponent<C>> commandComponents,
            final @NonNull CommandExecutionHandler<@NonNull C> commandExecutionHandler,
            final @NonNull CommandPermission commandPermission,
            final @NonNull CommandMeta commandMeta
    ) {
        this(commandComponents, commandExecutionHandler, null, commandPermission, commandMeta);
    }

    /**
     * Creates a new command builder.
     * <p>
     * Is recommended to use the builder methods in {@link CommandManager} rather than invoking this method directly.
     *
     * @param commandName base command argument
     * @param commandMeta command meta instance
     * @param description command description
     * @param aliases     command aliases
     * @param <C>         command sender type
     * @return command builder
     * @since 1.4.0
     */
    @API(status = API.Status.STABLE, since = "1.4.0")
    public static <C> @NonNull Builder<C> newBuilder(
            final @NonNull String commandName,
            final @NonNull CommandMeta commandMeta,
            final @NonNull ArgumentDescription description,
            final @NonNull String @NonNull... aliases
    ) {
        final List<CommandComponent<C>> commands = new ArrayList<>();
        final ParserDescriptor<C, String> staticParser = LiteralParser.literal(commandName, aliases);
        commands.add(
                CommandComponent.<C, String>builder()
                        .name(commandName)
                        .parser(staticParser)
                        .description(description)
                        .build()
        );
        return new Builder<>(
                null,
                commandMeta,
                null,
                commands,
                new CommandExecutionHandler.NullCommandExecutionHandler<>(),
                Permission.empty(),
                Collections.emptyList()
        );
    }

    /**
     * Creates a new command builder.
     * <p>
     * Is recommended to use the builder methods in {@link CommandManager} rather than invoking this method directly.
     *
     * @param commandName base command argument
     * @param commandMeta command meta instance
     * @param aliases     command aliases
     * @param <C>         command sender type
     * @return command builder
     */
    public static <C> @NonNull Builder<C> newBuilder(
            final @NonNull String commandName,
            final @NonNull CommandMeta commandMeta,
            final @NonNull String @NonNull... aliases
    ) {
        final List<CommandComponent<C>> commands = new ArrayList<>();
        final ParserDescriptor<C, String> staticParser = LiteralParser.literal(commandName, aliases);
        commands.add(
                CommandComponent.<C, String>builder()
                        .name(commandName)
                        .parser(staticParser)
                        .build()
        );
        return new Builder<>(
                null,
                commandMeta,
                null,
                commands,
                new CommandExecutionHandler.NullCommandExecutionHandler<>(),
                Permission.empty(),
                Collections.emptyList()
        );
    }

    /**
     * Returns a copy of the list of the components that make up this command.
     *
     * @return modifiable copy of the component list
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull List<CommandComponent<C>> components() {
        return new ArrayList<>(this.components);
    }

    /**
     * Returns the first command component.
     *
     * @return the root component
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull CommandComponent<C> rootComponent() {
        return this.components.get(0);
    }

    /**
     * Returns a mutable copy of the command components, ignoring flag arguments.
     *
     * @return argument list
     * @since 1.8.0
     */
    @API(status = API.Status.EXPERIMENTAL, since = "1.8.0")
    public @NonNull List<CommandComponent<C>> nonFlagArguments() {
        final List<CommandComponent<C>> components = new ArrayList<>(this.components);
        if (this.flagComponent() != null) {
            components.remove(this.flagComponent());
        }
        return components;
    }

    /**
     * Returns the component that contains the flags belonging to the command.
     *
     * @return the flag component, or {@code null} if no flags have been registered
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @Nullable CommandComponent<C> flagComponent() {
        return this.flagComponent;
    }

    /**
     * Returns the flag parser for this command, of {@code null} if the command has no flags.
     *
     * @return flag parser, or {@code null} if no flags have been registered
     * @since 2.0.0
     */
    @SuppressWarnings("unchecked")
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @Nullable CommandFlagParser<@NonNull C> flagParser() {
        final CommandComponent<C> flagComponent = this.flagComponent();
        if (flagComponent == null) {
            return null;
        }
        return (CommandFlagParser<C>) flagComponent.parser();
    }

    /**
     * Returns the command execution handler.
     * <p>
     * The command execution handler is invoked after a parsing a command.
     * It has access to the {@link cloud.commandframework.context.CommandContext} which contains
     * the parsed component values.
     *
     * @return the command execution handler
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull CommandExecutionHandler<@NonNull C> commandExecutionHandler() {
        return this.commandExecutionHandler;
    }

    /**
     * Returns the specific command sender type for the command if one has been defined.
     * <p>
     * A command may have a sender that is different from the sender type of the command manager.
     * The command tree will enforce this type when parsing the command.
     *
     * @return the special sender type for the command, or {@link Optional#empty()} if the command uses the same sender type
     * as the command manager
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull Optional<Class<? extends C>> senderType() {
        return Optional.ofNullable(this.senderType);
    }

    /**
     * Returns the permission required to execute the command.
     * <p>
     * If the sender does not have the required permission a {@link cloud.commandframework.exceptions.NoPermissionException}
     * will be thrown when parsing the command.
     *
     * @return the command permission
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull CommandPermission commandPermission() {
        return this.commandPermission;
    }

    /**
     * Returns the meta-data associated with the command.
     * <p>
     * A common way of using the command meta is by using it to filter out commands in post-processing.
     *
     * @return Command meta
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull CommandMeta commandMeta() {
        return this.commandMeta;
    }

    @Override
    public final String toString() {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final CommandComponent<C> component : this.components()) {
            stringBuilder.append(component.name()).append(' ');
        }
        final String build = stringBuilder.toString();
        return build.substring(0, build.length() - 1);
    }

    /**
     * Returns whether the command is hidden.
     *
     * @return {@code true} if the command is hidden, {@code false} if not
     */
    public boolean isHidden() {
        return this.commandMeta().getOrDefault(CommandMeta.HIDDEN, false);
    }


    /**
     * Builder for {@link Command} instances. The builder is immutable, and each
     * setter method will return a new builder instance.
     *
     * @param <C> Command sender type
     */
    @API(status = API.Status.STABLE)
    public static final class Builder<C> {

        private final CommandMeta commandMeta;
        private final List<CommandComponent<C>> commandComponents;
        private final CommandExecutionHandler<C> commandExecutionHandler;
        private final Class<? extends C> senderType;
        private final CommandPermission commandPermission;
        private final CommandManager<C> commandManager;
        private final Collection<CommandFlag<?>> flags;

        private Builder(
                final @Nullable CommandManager<C> commandManager,
                final @NonNull CommandMeta commandMeta,
                final @Nullable Class<? extends C> senderType,
                final @NonNull List<@NonNull CommandComponent<C>> commandComponents,
                final @NonNull CommandExecutionHandler<@NonNull C> commandExecutionHandler,
                final @NonNull CommandPermission commandPermission,
                final @NonNull Collection<CommandFlag<?>> flags
        ) {
            this.commandManager = commandManager;
            this.senderType = senderType;
            this.commandComponents = Objects.requireNonNull(commandComponents, "Components may not be null");
            this.commandExecutionHandler = Objects.requireNonNull(commandExecutionHandler, "Execution handler may not be null");
            this.commandPermission = Objects.requireNonNull(commandPermission, "Permission may not be null");
            this.commandMeta = Objects.requireNonNull(commandMeta, "Meta may not be null");
            this.flags = Objects.requireNonNull(flags, "Flags may not be null");
        }

        /**
         * Returns the required sender type for this builder.
         * <p>
         * Returns {@code null} when there is not a specific required sender type.
         *
         * @return required sender type
         * @since 1.3.0
         */
        @API(status = API.Status.STABLE, since = "1.3.0")
        public @Nullable Class<? extends C> senderType() {
            return this.senderType;
        }

        /**
         * Returns the required command permission for this builder.
         * <p>
         * Will return {@link Permission#empty()} if there is no required permission.
         *
         * @return required permission
         * @since 1.3.0
         */
        @API(status = API.Status.STABLE, since = "1.3.0")
        public @NonNull CommandPermission commandPermission() {
            return this.commandPermission;
        }

        /**
         * Applies the provided {@link Applicable} to this {@link Builder}, and returns the result.
         *
         * @param applicable operation
         * @return operation result
         * @since 1.8.0
         */
        @API(status = API.Status.STABLE, since = "1.8.0")
        public @NonNull Builder<@NonNull C> apply(
                final @NonNull Applicable<@NonNull C> applicable
        ) {
            return applicable.applyToCommandBuilder(this);
        }

        /**
         * Adds command meta to the internal command meta-map.
         *
         * @param <V>   meta value type
         * @param key   meta key
         * @param value meta value
         * @return new builder instance using the inserted meta key-value pair
         * @since 1.3.0
         */
        @API(status = API.Status.STABLE, since = "1.3.0")
        public <V> @NonNull Builder<C> meta(final CommandMeta.@NonNull Key<V> key, final @NonNull V value) {
            final CommandMeta commandMeta = SimpleCommandMeta.builder().with(this.commandMeta).with(key, value).build();
            return new Builder<>(
                    this.commandManager,
                    commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    this.commandPermission,
                    this.flags
            );
        }

        /**
         * Supplies a command manager instance to the builder.
         * <p>
         * This will be used when attempting to
         * retrieve command argument parsers, in the case that they're needed.
         * <p>
         * This is optional.
         *
         * @param commandManager Command manager
         * @return new builder instance using the provided command manager
         */
        public @NonNull Builder<C> manager(final @Nullable CommandManager<C> commandManager) {
            return new Builder<>(
                    commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    this.commandPermission,
                    this.flags
            );
        }

        /**
         * Inserts a required literal into the command chain.
         *
         * @param main    main argument name
         * @param aliases argument aliases
         * @return new builder instance with the modified command chain
         */
        public @NonNull Builder<C> literal(
                final @NonNull String main,
                final @NonNull String... aliases
        ) {
            return this.required(main, LiteralParser.literal(main, aliases));
        }

        /**
         * Inserts a required literal into the command chain.
         *
         * @param main        main argument name
         * @param description literal description
         * @param aliases     argument aliases
         * @return new builder instance with the modified command chain
         * @since 1.4.0
         */
        @API(status = API.Status.STABLE, since = "1.4.0")
        public @NonNull Builder<C> literal(
                final @NonNull String main,
                final @NonNull ArgumentDescription description,
                final @NonNull String... aliases
        ) {
            return this.required(main, LiteralParser.literal(main, aliases), description);
        }

        /**
         * Adds the given required {@code argument} to the command
         *
         * @param argument    argument to add
         * @param description description of the argument
         * @param <U>         type of the argument
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U extends CloudKeyHolder & ParserDescriptor> @NonNull Builder<C> required(
                final @NonNull U argument,
                final @NonNull ArgumentDescription description
        ) {
            final CommandComponent.Builder builder = CommandComponent.builder()
                    .key(argument.getKey())
                    .parser(argument)
                    .description(description);
            if (argument instanceof SuggestionProvider) {
                builder.suggestionProvider((SuggestionProvider<C>) argument);
            }
            if (argument instanceof PreprocessorHolder) {
                builder.preprocessors(((PreprocessorHolder) argument).preprocessors());
            }
            return this.argument(builder);
        }

        /**
         * Marks the {@code builder} as required and adds it to the command.
         *
         * @param name    the name that will be inserted into the builder
         * @param builder the component builder
         * @return new builder instance with the command argument inserted into the argument list
         */
        @SuppressWarnings({"rawtypes"})
        @API(status = API.Status.STABLE, since = "2.0.0")
        public @NonNull Builder<C> required(
                final @NonNull String name,
                final CommandComponent.@NonNull Builder builder
        ) {
            return this.argument(builder.name(name).required());
        }

        /**
         * Marks the {@code builder} as required and adds it to the command.
         *
         * @param name    the name that will be inserted into the builder
         * @param builder the component builder
         * @return new builder instance with the command argument inserted into the argument list
         */
        @SuppressWarnings({"rawtypes"})
        @API(status = API.Status.STABLE, since = "2.0.0")
        public @NonNull Builder<C> optional(
                final @NonNull String name,
                final CommandComponent.@NonNull Builder builder
        ) {
            return this.argument(builder.name(name).optional());
        }

        /**
         * Marks the {@code builder} as required and adds it to the command.
         *
         * @param builder the component builder
         * @return new builder instance with the command argument inserted into the argument list
         */
        @SuppressWarnings({"rawtypes"})
        @API(status = API.Status.STABLE, since = "2.0.0")
        public @NonNull Builder<C> required(
                final CommandComponent.@NonNull Builder builder
        ) {
            return this.argument(builder.required());
        }

        /**
         * Marks the {@code builder} as required and adds it to the command.
         *
         * @param builder the component builder
         * @return new builder instance with the command argument inserted into the argument list
         */
        @SuppressWarnings({"rawtypes"})
        @API(status = API.Status.STABLE, since = "2.0.0")
        public @NonNull Builder<C> optional(
                final CommandComponent.@NonNull Builder builder
        ) {
            return this.argument(builder.optional());
        }

        /**
         * Adds the given optional {@code argument} to the command with no default value.
         *
         * @param argument    argument to add
         * @param description description of the argument
         * @param <U>         type of the argument
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U extends CloudKeyHolder & ParserDescriptor> @NonNull Builder<C> optional(
                final @NonNull U argument,
                final @NonNull ArgumentDescription description
        ) {
            final CommandComponent.Builder builder = CommandComponent.builder()
                    .key(argument.getKey())
                    .parser(argument)
                    .optional()
                    .description(description);
            if (argument instanceof SuggestionProvider) {
                builder.suggestionProvider((SuggestionProvider<C>) argument);
            }
            if (argument instanceof PreprocessorHolder) {
                builder.preprocessors(((PreprocessorHolder) argument).preprocessors());
            }
            return this.argument(builder);
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param argument the argument
         * @param <U>      type of the argument
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U extends CloudKeyHolder & ParserDescriptor> @NonNull Builder<C> required(
                final @NonNull U argument
        ) {
            final CommandComponent.Builder builder = CommandComponent.builder()
                    .key(argument.getKey())
                    .parser(argument);
            if (argument instanceof SuggestionProvider) {
                builder.suggestionProvider((SuggestionProvider<C>) argument);
            }
            if (argument instanceof PreprocessorHolder) {
                builder.preprocessors(((PreprocessorHolder) argument).preprocessors());
            }
            return this.argument(builder);
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param argument the argument
         * @param <U>      type of the argument
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U extends CloudKeyHolder & ParserDescriptor> @NonNull Builder<C> optional(
                final @NonNull U argument
        ) {
            final CommandComponent.Builder builder = CommandComponent.builder()
                    .key(argument.getKey())
                    .parser(argument)
                    .optional();
            if (argument instanceof SuggestionProvider) {
                builder.suggestionProvider((SuggestionProvider<C>) argument);
            }
            if (argument instanceof PreprocessorHolder) {
                builder.preprocessors(((PreprocessorHolder) argument).preprocessors());
            }
            return this.argument(builder);
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name   the name of the argument
         * @param parser the parser
         * @param <T>    the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> required(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser
        ) {
            return this.argument(CommandComponent.<C, T>builder().name(name).parser(parser).build());
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> required(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .name(name)
                            .parser(parser)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name   the name of the argument
         * @param parser the parser
         * @param <T>    the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> required(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser
        ) {
            return this.argument(CommandComponent.<C, T>builder().key(name).parser(parser).build());
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> required(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .key(name)
                            .parser(parser)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> required(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull ArgumentDescription description
        ) {
            return this.argument(CommandComponent.<C, T>builder().key(name).parser(parser).description(description).build());
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> required(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull ArgumentDescription description,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .key(name)
                            .parser(parser)
                            .description(description)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> required(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull ArgumentDescription description
        ) {
            return this.argument(CommandComponent.<C, T>builder().name(name).parser(parser).description(description).build());
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> required(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull ArgumentDescription description,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .name(name)
                            .parser(parser)
                            .description(description)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }


        /**
         * Adds the given optional argument to the command.
         *
         * @param name   the name of the argument
         * @param parser the parser
         * @param <T>    the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser
        ) {
            return this.argument(CommandComponent.<C, T>builder().name(name).parser(parser).optional().build());
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .name(name)
                            .parser(parser)
                            .optional()
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name   the name of the argument
         * @param parser the parser
         * @param <T>    the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser
        ) {
            return this.argument(CommandComponent.<C, T>builder().key(name).parser(parser).optional().build());
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .key(name)
                            .parser(parser)
                            .optional()
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull ArgumentDescription description
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .name(name)
                            .parser(parser)
                            .description(description)
                            .optional()
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull ArgumentDescription description,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .name(name)
                            .parser(parser)
                            .description(description)
                            .optional()
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull ArgumentDescription description
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .key(name)
                            .parser(parser)
                            .description(description)
                            .optional()
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull ArgumentDescription description,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .key(name)
                            .parser(parser)
                            .description(description)
                            .optional()
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull DefaultValue<C, T> defaultValue
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .name(name)
                            .parser(parser)
                            .optional(defaultValue)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param suggestions  the suggestion provider
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull DefaultValue<C, T> defaultValue,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .name(name)
                            .parser(parser)
                            .optional(defaultValue)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull DefaultValue<C, T> defaultValue
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .key(name)
                            .parser(parser)
                            .optional(defaultValue)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param suggestions  the suggestion provider
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull DefaultValue<C, T> defaultValue,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .key(name)
                            .parser(parser)
                            .optional(defaultValue)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param description  the description of the argument
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull DefaultValue<C, T> defaultValue,
                final @NonNull ArgumentDescription description
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .name(name)
                            .parser(parser)
                            .optional(defaultValue)
                            .description(description)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param description  the description of the argument
         * @param suggestions  the suggestion provider
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull DefaultValue<C, T> defaultValue,
                final @NonNull ArgumentDescription description,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .name(name)
                            .parser(parser)
                            .optional(defaultValue)
                            .description(description)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param description  the description of the argument
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull DefaultValue<C, T> defaultValue,
                final @NonNull ArgumentDescription description
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .key(name)
                            .parser(parser)
                            .optional(defaultValue)
                            .description(description)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param description  the description of the argument
         * @param suggestions  the suggestion provider
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<C, T> parser,
                final @NonNull DefaultValue<C, T> defaultValue,
                final @NonNull ArgumentDescription description,
                final @NonNull SuggestionProvider<C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder()
                            .key(name)
                            .parser(parser)
                            .optional(defaultValue)
                            .description(description)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds a new required command argument by interacting with a constructed command argument builder.
         *
         * @param clazz           argument class
         * @param name            argument name
         * @param builderConsumer builder consumer
         * @param <T>             argument type
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> required(
                final @NonNull Class<T> clazz,
                final @NonNull String name,
                final @NonNull Consumer<CommandComponent.Builder<C, T>> builderConsumer
        ) {
            final CommandComponent.Builder<C, T> builder = CommandComponent.ofType(clazz, name);
            builderConsumer.accept(builder);
            return this.argument(builder);
        }

        /**
         * Adds a new optional command argument by interacting with a constructed command argument builder.
         *
         * @param clazz           argument class
         * @param name            argument name
         * @param builderConsumer builder consumer
         * @param <T>             argument type
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <T> @NonNull Builder<C> optional(
                final @NonNull Class<T> clazz,
                final @NonNull String name,
                final @NonNull Consumer<CommandComponent.Builder<C, T>> builderConsumer
        ) {
            final CommandComponent.Builder<C, T> builder = CommandComponent.ofType(clazz, name);
            builderConsumer.accept(builder);
            return this.argument(builder.optional());
        }

        /**
         * Adds the given {@code argument} to the command.
         * <p>
         * The component will be copied using {@link CommandComponent#copy()} before being inserted into the command tree.
         *
         * @param argument argument to add
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public @NonNull Builder<C> argument(
                final @NonNull CommandComponent<C> argument
        ) {
            final List<CommandComponent<C>> commandComponents = new ArrayList<>(this.commandComponents);
            commandComponents.add(argument.copy());
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    commandComponents,
                    this.commandExecutionHandler,
                    this.commandPermission,
                    this.flags
            );
        }

        /**
         * Adds the given {@code argument} to the command.
         * <p>
         * The component will be copied using {@link CommandComponent#copy()} before being inserted into the command tree.
         *
         * @param builder builder that builds the component to add
         * @return new builder instance with the command argument inserted into the argument list
         * @since 2.0.0
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        @API(status = API.Status.STABLE, since = "2.0.0")
        public @NonNull Builder<C> argument(
                final CommandComponent.Builder builder
        ) {
            if (this.commandManager != null) {
                return this.argument(builder.commandManager(this.commandManager).build());
            } else {
                return this.argument(builder.build());
            }
        }

        // Compound helper methods

        /**
         * Creates a new argument pair that maps to {@link Pair}.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}..
         *
         * @param name        name of the argument
         * @param names       pair containing the names of the sub-arguments
         * @param parserPair  pair containing the types of the sub-arguments. There must be parsers for these types registered
         *                    in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by the
         *                    {@link CommandManager} attached to this command
         * @param description description of the argument
         * @param <U>         first type
         * @param <V>         second type
         * @return new builder instance with the argument inserted
         * @since 1.4.0
         */
        @API(status = API.Status.STABLE, since = "1.4.0")
        public <U, V> @NonNull Builder<C> requiredArgumentPair(
                final @NonNull String name,
                final @NonNull Pair<@NonNull String, @NonNull String> names,
                final @NonNull Pair<@NonNull Class<U>, @NonNull Class<V>> parserPair,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    ArgumentPair.of(this.commandManager, names, parserPair).simple(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to {@link Pair}.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}..
         *
         * @param name        name of the argument
         * @param names       pair containing the names of the sub-arguments
         * @param parserPair  pair containing the types of the sub-arguments. There must be parsers for these types registered
         *                    in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by the
         *                    {@link CommandManager} attached to this command
         * @param description description of the argument
         * @param <U>         first type
         * @param <V>         second type
         * @return new builder instance with the argument inserted
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U, V> @NonNull Builder<C> requiredArgumentPair(
                final @NonNull CloudKey<Pair<U, V>> name,
                final @NonNull Pair<@NonNull String, @NonNull String> names,
                final @NonNull Pair<@NonNull Class<U>, @NonNull Class<V>> parserPair,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    ArgumentPair.of(this.commandManager, names, parserPair).simple(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to {@link Pair}.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}..
         *
         * @param name        name of the argument
         * @param names       pair containing the names of the sub-arguments
         * @param parserPair  pair containing the types of the sub-arguments. There must be parsers for these types registered
         *                    in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by the
         *                    {@link CommandManager} attached to this command
         * @param description description of the argument
         * @param <U>         first type
         * @param <V>         second type
         * @return new builder instance with the argument inserted
         * @since 1.4.0
         */
        @API(status = API.Status.STABLE, since = "1.4.0")
        public <U, V> @NonNull Builder<C> optionalArgumentPair(
                final @NonNull String name,
                final @NonNull Pair<@NonNull String, @NonNull String> names,
                final @NonNull Pair<@NonNull Class<U>, @NonNull Class<V>> parserPair,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    ArgumentPair.of(this.commandManager, names, parserPair).simple(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to {@link Pair}.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}..
         *
         * @param name        name of the argument
         * @param names       pair containing the names of the sub-arguments
         * @param parserPair  pair containing the types of the sub-arguments. There must be parsers for these types registered
         *                    in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by the
         *                    {@link CommandManager} attached to this command
         * @param description description of the argument
         * @param <U>         first type
         * @param <V>         second type
         * @return new builder instance with the argument inserted
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U, V> @NonNull Builder<C> optionalArgumentPair(
                final @NonNull CloudKey<Pair<U, V>> name,
                final @NonNull Pair<@NonNull String, @NonNull String> names,
                final @NonNull Pair<@NonNull Class<U>, @NonNull Class<V>> parserPair,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    ArgumentPair.of(this.commandManager, names, parserPair).simple(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}..
         *
         * @param name        name of the argument
         * @param outputType  the output type
         * @param names       pair containing the names of the sub-arguments
         * @param parserPair  pair containing the types of the sub-arguments. There must be parsers for these types registered
         *                    in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by the
         *                    {@link CommandManager} attached to this command
         * @param mapper      mapper that maps from {@link Pair} to the custom type
         * @param description description of the argument
         * @param <U>         first type
         * @param <V>         second type
         * @param <O>         output type
         * @return new builder instance with the argument inserted
         * @since 1.4.0
         */
        @API(status = API.Status.STABLE, since = "1.4.0")
        public <U, V, O> @NonNull Builder<C> requiredArgumentPair(
                final @NonNull String name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull Pair<String, String> names,
                final @NonNull Pair<Class<U>, Class<V>> parserPair,
                final @NonNull BiFunction<C, Pair<U, V>, O> mapper,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    ArgumentPair.of(this.commandManager, names, parserPair).withMapper(outputType, mapper),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}..
         *
         * @param name        name of the argument
         * @param outputType  the output type
         * @param names       pair containing the names of the sub-arguments
         * @param parserPair  pair containing the types of the sub-arguments. There must be parsers for these types registered
         *                    in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by the
         *                    {@link CommandManager} attached to this command
         * @param mapper      mapper that maps from {@link Pair} to the custom type
         * @param description description of the argument
         * @param <U>         first type
         * @param <V>         second type
         * @param <O>         output type
         * @return new builder instance with the argument inserted
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U, V, O> @NonNull Builder<C> requiredArgumentPair(
                final @NonNull CloudKey<O> name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull Pair<String, String> names,
                final @NonNull Pair<Class<U>, Class<V>> parserPair,
                final @NonNull BiFunction<C, Pair<U, V>, O> mapper,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    ArgumentPair.of(this.commandManager, names, parserPair).withMapper(outputType, mapper),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name        name of the argument
         * @param outputType  the output type
         * @param names       pair containing the names of the sub-arguments
         * @param parserPair  pair containing the types of the sub-arguments. There must be parsers for these types registered
         *                    in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by the
         *                    {@link CommandManager} attached to this command
         * @param mapper      mapper that maps from {@link Pair} to the custom type
         * @param description description of the argument
         * @param <U>         first type
         * @param <V>         second type
         * @param <O>         output type
         * @return new builder instance with the argument inserted
         * @since 1.4.0
         */
        @API(status = API.Status.STABLE, since = "1.4.0")
        public <U, V, O> @NonNull Builder<C> optionalArgumentPair(
                final @NonNull String name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull Pair<String, String> names,
                final @NonNull Pair<Class<U>, Class<V>> parserPair,
                final @NonNull BiFunction<C, Pair<U, V>, O> mapper,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    ArgumentPair.of(this.commandManager, names, parserPair).withMapper(outputType, mapper),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name        name of the argument
         * @param outputType  the output type
         * @param names       pair containing the names of the sub-arguments
         * @param parserPair  pair containing the types of the sub-arguments. There must be parsers for these types registered
         *                    in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by the
         *                    {@link CommandManager} attached to this command
         * @param mapper      mapper that maps from {@link Pair} to the custom type
         * @param description description of the argument
         * @param <U>         first type
         * @param <V>         second type
         * @param <O>         output type
         * @return new builder instance with the argument inserted
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U, V, O> @NonNull Builder<C> optionalArgumentPair(
                final @NonNull CloudKey<O> name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull Pair<String, String> names,
                final @NonNull Pair<Class<U>, Class<V>> parserPair,
                final @NonNull BiFunction<C, Pair<U, V>, O> mapper,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    ArgumentPair.of(this.commandManager, names, parserPair).withMapper(outputType, mapper),
                    description
            );
        }

        /**
         * Create a new argument pair that maps to {@link cloud.commandframework.types.tuples.Triplet}
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name          name of the argument
         * @param names         triplet containing the names of the sub-arguments
         * @param parserTriplet triplet containing the types of the sub-arguments. There must be parsers for these types
         *                      registered in the {@link cloud.commandframework.arguments.parser.ParserRegistry}
         *                      used by the {@link CommandManager} attached to this command
         * @param description   description of the argument
         * @param <U>           first type
         * @param <V>           second type
         * @param <W>           third type
         * @return new builder instance with the argument inserted
         * @since 1.4.0
         */
        @API(status = API.Status.STABLE, since = "1.4.0")
        public <U, V, W> @NonNull Builder<C> requiredArgumentTriplet(
                final @NonNull String name,
                final @NonNull Triplet<String, String, String> names,
                final @NonNull Triplet<Class<U>, Class<V>, Class<W>> parserTriplet,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    ArgumentTriplet.of(this.commandManager, names, parserTriplet).simple(),
                    description
            );
        }

        /**
         * Create a new argument pair that maps to {@link cloud.commandframework.types.tuples.Triplet}
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name          name of the argument
         * @param names         triplet containing the names of the sub-arguments
         * @param parserTriplet triplet containing the types of the sub-arguments. There must be parsers for these types
         *                      registered in the {@link cloud.commandframework.arguments.parser.ParserRegistry}
         *                      used by the {@link CommandManager} attached to this command
         * @param description   description of the argument
         * @param <U>           first type
         * @param <V>           second type
         * @param <W>           third type
         * @return new builder instance with the argument inserted
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U, V, W> @NonNull Builder<C> requiredArgumentTriplet(
                final @NonNull CloudKey<Triplet<U, V, W>> name,
                final @NonNull Triplet<String, String, String> names,
                final @NonNull Triplet<Class<U>, Class<V>, Class<W>> parserTriplet,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    ArgumentTriplet.of(this.commandManager, names, parserTriplet).simple(),
                    description
            );
        }

        /**
         * Create a new argument pair that maps to {@link cloud.commandframework.types.tuples.Triplet}
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name          name of the argument
         * @param names         triplet containing the names of the sub-arguments
         * @param parserTriplet triplet containing the types of the sub-arguments. There must be parsers for these types
         *                      registered in the {@link cloud.commandframework.arguments.parser.ParserRegistry}
         *                      used by the {@link CommandManager} attached to this command
         * @param description   description of the argument
         * @param <U>           first type
         * @param <V>           second type
         * @param <W>           third type
         * @return new builder instance with the argument inserted
         * @since 1.4.0
         */
        @API(status = API.Status.STABLE, since = "1.4.0")
        public <U, V, W> @NonNull Builder<C> optionalArgumentTriplet(
                final @NonNull String name,
                final @NonNull Triplet<String, String, String> names,
                final @NonNull Triplet<Class<U>, Class<V>, Class<W>> parserTriplet,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    ArgumentTriplet.of(this.commandManager, names, parserTriplet).simple(),
                    description
            );
        }

        /**
         * Create a new argument pair that maps to {@link cloud.commandframework.types.tuples.Triplet}
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name          name of the argument
         * @param names         triplet containing the names of the sub-arguments
         * @param parserTriplet triplet containing the types of the sub-arguments. There must be parsers for these types
         *                      registered in the {@link cloud.commandframework.arguments.parser.ParserRegistry}
         *                      used by the {@link CommandManager} attached to this command
         * @param description   description of the argument
         * @param <U>           first type
         * @param <V>           second type
         * @param <W>           third type
         * @return new builder instance with the argument inserted
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U, V, W> @NonNull Builder<C> optionalArgumentTriplet(
                final @NonNull CloudKey<Triplet<U, V, W>> name,
                final @NonNull Triplet<String, String, String> names,
                final @NonNull Triplet<Class<U>, Class<V>, Class<W>> parserTriplet,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    ArgumentTriplet.of(this.commandManager, names, parserTriplet).simple(),
                    description
            );
        }

        /**
         * Creates a new argument triplet that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name          name of the argument
         * @param outputType    the output type
         * @param names         triplet containing the names of the sub-arguments
         * @param parserTriplet triplet containing the types of the sub-arguments. There must be parsers for these types
         *                      registered in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by
         *                      the {@link CommandManager} attached to this command
         * @param mapper        mapper that maps from {@link Triplet} to the custom type
         * @param description   description of the argument
         * @param <U>           first type
         * @param <V>           second type
         * @param <W>           third type
         * @param <O>           output type
         * @return new builder instance with the argument inserted
         * @since 1.4.0
         */
        @API(status = API.Status.STABLE, since = "1.4.0")
        public <U, V, W, O> @NonNull Builder<C> requiredArgumentTriplet(
                final @NonNull String name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull Triplet<String, String, String> names,
                final @NonNull Triplet<Class<U>, Class<V>, Class<W>> parserTriplet,
                final @NonNull BiFunction<C, Triplet<U, V, W>, O> mapper,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    ArgumentTriplet.of(this.commandManager, names, parserTriplet).withMapper(outputType, mapper),
                    description
            );
        }

        /**
         * Creates a new argument triplet that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name          name of the argument
         * @param outputType    the output type
         * @param names         triplet containing the names of the sub-arguments
         * @param parserTriplet triplet containing the types of the sub-arguments. There must be parsers for these types
         *                      registered in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by
         *                      the {@link CommandManager} attached to this command
         * @param mapper        Mapper that maps from {@link Triplet} to the custom type
         * @param description   description of the argument
         * @param <U>           first type
         * @param <V>           second type
         * @param <W>           third type
         * @param <O>           output type
         * @return new builder instance with the argument inserted
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U, V, W, O> @NonNull Builder<C> requiredArgumentTriplet(
                final @NonNull CloudKey<O> name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull Triplet<String, String, String> names,
                final @NonNull Triplet<Class<U>, Class<V>, Class<W>> parserTriplet,
                final @NonNull BiFunction<C, Triplet<U, V, W>, O> mapper,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    ArgumentTriplet.of(this.commandManager, names, parserTriplet).withMapper(outputType, mapper),
                    description
            );
        }

        /**
         * Creates a new argument triplet that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name          name of the argument
         * @param outputType    the output type
         * @param names         triplet containing the names of the sub-arguments
         * @param parserTriplet triplet containing the types of the sub-arguments. There must be parsers for these types
         *                      registered in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by
         *                      the {@link CommandManager} attached to this command
         * @param mapper        mapper that maps from {@link Triplet} to the custom type
         * @param description   description of the argument
         * @param <U>           first type
         * @param <V>           second type
         * @param <W>           third type
         * @param <O>           output type
         * @return new builder instance with the argument inserted
         * @since 1.4.0
         */
        @API(status = API.Status.STABLE, since = "1.4.0")
        public <U, V, W, O> @NonNull Builder<C> optionalArgumentTriplet(
                final @NonNull String name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull Triplet<String, String, String> names,
                final @NonNull Triplet<Class<U>, Class<V>, Class<W>> parserTriplet,
                final @NonNull BiFunction<C, Triplet<U, V, W>, O> mapper,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    ArgumentTriplet.of(this.commandManager, names, parserTriplet).withMapper(outputType, mapper),
                    description
            );
        }

        /**
         * Creates a new argument triplet that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name          name of the argument
         * @param outputType    the output type
         * @param names         triplet containing the names of the sub-arguments
         * @param parserTriplet triplet containing the types of the sub-arguments. There must be parsers for these types
         *                      registered in the {@link cloud.commandframework.arguments.parser.ParserRegistry} used by
         *                      the {@link CommandManager} attached to this command
         * @param mapper        mapper that maps from {@link Triplet} to the custom type
         * @param description   description of the argument
         * @param <U>           first type
         * @param <V>           second type
         * @param <W>           third type
         * @param <O>           output type
         * @return new builder instance with the argument inserted
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public <U, V, W, O> @NonNull Builder<C> optionalArgumentTriplet(
                final @NonNull CloudKey<O> name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull Triplet<String, String, String> names,
                final @NonNull Triplet<Class<U>, Class<V>, Class<W>> parserTriplet,
                final @NonNull BiFunction<C, Triplet<U, V, W>, O> mapper,
                final @NonNull ArgumentDescription description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    ArgumentTriplet.of(this.commandManager, names, parserTriplet).withMapper(outputType, mapper),
                    description
            );
        }

        // End of compound helper methods

        /**
         * Specifies the command execution handler.
         *
         * @param commandExecutionHandler New execution handler
         * @return new builder instance using the command execution handler
         */
        public @NonNull Builder<C> handler(final @NonNull CommandExecutionHandler<C> commandExecutionHandler) {
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    commandExecutionHandler,
                    this.commandPermission,
                    this.flags
            );
        }

        /**
         * Returns the current command execution handler.
         *
         * @return the current handler
         * @since 1.7.0
         */
        @API(status = API.Status.STABLE, since = "1.7.0")
        public @NonNull CommandExecutionHandler<C> handler() {
            return this.commandExecutionHandler;
        }

        /**
         * Sets a new command execution handler that invokes the given {@code handler} before the current
         * {@link #handler() handler}.
         *
         * @param handler the handler to invoke before the current handler
         * @return new builder instance
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public @NonNull Builder<C> prependHandler(final @NonNull CommandExecutionHandler<C> handler) {
            return this.handler(CommandExecutionHandler.delegatingExecutionHandler(Arrays.asList(handler, this.handler())));
        }

        /**
         * Sets a new command execution handler that invokes the given {@code handler} after the current
         * {@link #handler() handler}.
         *
         * @param handler the handler to invoke after the current handler
         * @return new builder instance
         * @since 2.0.0
         */
        @API(status = API.Status.STABLE, since = "2.0.0")
        public @NonNull Builder<C> appendHandler(final @NonNull CommandExecutionHandler<C> handler) {
            return this.handler(CommandExecutionHandler.delegatingExecutionHandler(Arrays.asList(this.handler(), handler)));
        }

        /**
         * Specifies a required sender type.
         *
         * @param <N>        the new sender type or a superclass thereof
         * @param senderType required sender type
         * @return new builder instance using the required sender type
         */
        @SuppressWarnings("unchecked")
        public <N extends C> @NonNull Builder<N> senderType(final @NonNull Class<? extends N> senderType) {
            return (Builder<N>) new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    this.commandPermission,
                    this.flags
            );
        }

        /**
         * Specifies a command permission.
         *
         * @param permission the command permission
         * @return new builder instance using the command permission
         */
        public @NonNull Builder<C> permission(final @NonNull CommandPermission permission) {
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    permission,
                    this.flags
            );
        }

        /**
         * Specifies a command permission.
         *
         * @param permission the command permission
         * @return new builder instance using the command permission
         */
        public @NonNull Builder<C> permission(final @NonNull PredicatePermission<C> permission) {
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    permission,
                    this.flags
            );
        }

        /**
         * Specifies a command permission.
         *
         * @param permission the command permission
         * @return new builder instance using the command permission
         */
        public @NonNull Builder<C> permission(final @NonNull String permission) {
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    Permission.of(permission),
                    this.flags
            );
        }

        /**
         * Makes the current command be a proxy of the supplied command. T
         * <p>
         * his means that all the proxied command's variable command arguments will be inserted into this
         * builder instance, in the order they are declared in the proxied command. Furthermore,
         * the proxied command's command handler will be shown by the command that is currently
         * being built. If the current command builder does not have a permission node set, this
         * too will be copied.
         *
         * @param command the command to proxy
         * @return new builder that proxies the given command
         */
        public @NonNull Builder<C> proxies(final @NonNull Command<C> command) {
            Builder<C> builder = this;
            for (final CommandComponent<C> component : command.components()) {
                if (component.type() == CommandComponent.ComponentType.LITERAL) {
                    continue;
                }
                final CommandComponent<C> componentCopy = component.copy();
                builder = builder.argument(componentCopy);
            }
            if (this.commandPermission.toString().isEmpty()) {
                builder = builder.permission(command.commandPermission());
            }
            return builder.handler(command.commandExecutionHandler);
        }

        /**
         * Indicates that the command should be hidden from help menus
         * and other places where commands are exposed to users.
         *
         * @return new builder instance that indicates that the constructed command should be hidden
         */
        public @NonNull Builder<C> hidden() {
            return this.meta(CommandMeta.HIDDEN, true);
        }

        /**
         * Registers a new command flag.
         *
         * @param flag flag
         * @param <T>  flag value type
         * @return new builder instance that uses the provided flag
         */
        public @NonNull <T> Builder<C> flag(final @NonNull CommandFlag<T> flag) {
            final List<CommandFlag<?>> flags = new ArrayList<>(this.flags);
            flags.add(flag);
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    this.commandPermission,
                    Collections.unmodifiableList(flags)
            );
        }

        /**
         * Registers a new command flag.
         *
         * @param builder flag builder. {@link CommandFlag.Builder#build()} will be invoked.
         * @param <T>     flag value type
         * @return new builder instance that uses the provided flag
         */
        public @NonNull <T> Builder<C> flag(final CommandFlag.@NonNull Builder<T> builder) {
            return this.flag(builder.build());
        }

        /**
         * Builds a command using the builder instance.
         *
         * @return built command
         */
        public @NonNull Command<C> build() {
            final List<CommandComponent<C>> commandComponents = new ArrayList<>(this.commandComponents);
            /* Construct flag node */
            if (!this.flags.isEmpty()) {
                final CommandFlagParser<C> flagParser = new CommandFlagParser<>(this.flags);
                final CommandComponent<C> flagComponent =
                        CommandComponent.<C, Object>builder()
                                .name("flags")
                                .parser(flagParser)
                                .valueType(Object.class)
                                .description(ArgumentDescription.of("Command flags"))
                                .build();
                commandComponents.add(flagComponent);
            }
            return new Command<>(
                    Collections.unmodifiableList(commandComponents),
                    this.commandExecutionHandler,
                    this.senderType,
                    this.commandPermission,
                    this.commandMeta
            );
        }

        /**
         * Essentially a {@link java.util.function.UnaryOperator} for {@link Builder},
         * but as a separate interface to avoid conflicts.
         *
         * @param <C> sender type
         * @since 1.8.0
         */
        @API(status = API.Status.STABLE, since = "1.8.0")
        @FunctionalInterface
        public interface Applicable<C> {

            /**
             * Accepts a {@link Builder} and returns either the same or a modified {@link Builder} instance.
             *
             * @param builder builder
             * @return possibly modified builder
             * @since 1.8.0
             */
            @API(status = API.Status.STABLE, since = "1.8.0")
            @NonNull Builder<C> applyToCommandBuilder(@NonNull Builder<C> builder);
        }
    }
}
