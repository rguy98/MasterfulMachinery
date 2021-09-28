package com.ticticboooom.mods.mm.client.jei.category;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.ticticboooom.mods.mm.MM;
import com.ticticboooom.mods.mm.block.ControllerBlock;
import com.ticticboooom.mods.mm.block.MachinePortBlock;
import com.ticticboooom.mods.mm.client.util.GuiBlockRenderBuilder;
import com.ticticboooom.mods.mm.data.MachineStructureRecipe;
import com.ticticboooom.mods.mm.data.model.structure.MachineStructureBlockPos;
import com.ticticboooom.mods.mm.data.model.structure.MachineStructurePort;
import com.ticticboooom.mods.mm.data.model.structure.MachineStructureRecipeKeyModel;
import com.ticticboooom.mods.mm.helper.RLUtils;
import com.ticticboooom.mods.mm.registration.MMLoader;
import com.ticticboooom.mods.mm.registration.MMSetup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DirectionalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.state.Property;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ITag;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class MachineStructureRecipeCategory implements IRecipeCategory<MachineStructureRecipe> {

    private static final ResourceLocation overlayRl = new ResourceLocation(MM.ID, "textures/gui/gui_large_jei.png");
    private static final ResourceLocation iconRl = new ResourceLocation(MM.ID, "textures/items/blueprint.png");
    private static final ResourceLocation slotRl = new ResourceLocation(MM.ID, "textures/gui/slot_parts.png");
    private IJeiHelpers helpers;
    private ControllerBlock controller;
    private MachineStructureRecipe recipe;
    private float xRotation = 0;
    private double xLastMousePosition = 0;
    private float yRotation = 0;
    private double yLastMousePosition = 0;
    private int scrollLastPos = 0;

    private int sliceY = 0;
    private boolean slicingActive = false;
    private float scaleFactor = 1F;

    private int tickTimer = 0;
    private Map<MachineStructureBlockPos, Integer> variantIndices = new HashMap<>();

    public MachineStructureRecipeCategory(IJeiHelpers helpers, ControllerBlock controller) {
        this.helpers = helpers;
        this.controller = controller;
    }

    @Override
    public ResourceLocation getUid() {
        return new ResourceLocation(MM.ID, "machine_structure_" + controller.getControllerId());
    }

    @Override
    public Class<? extends MachineStructureRecipe> getRecipeClass() {
        return MachineStructureRecipe.class;
    }

    @Override
    public String getTitle() {
        return controller.getControllerName();
    }

    @Override
    public IDrawable getBackground() {

        return helpers.getGuiHelper().createDrawable(overlayRl, 0, 0, 162, 150);
    }

    @Override
    public IDrawable getIcon() {
        return helpers.getGuiHelper().createDrawableIngredient(new ItemStack(MMSetup.BLUEPRINT.get(), 1));
    }

    private IDrawable getButton() {
        return helpers.getGuiHelper().createDrawable(slotRl, 0, 44, 18, 18);
    }

    @Override
    public void setIngredients(MachineStructureRecipe machineStructureRecipe, IIngredients iIngredients) {
        Ingredient ingredient = Ingredient.fromStacks(new ItemStack(MMSetup.BLUEPRINT.get()));
        ArrayList<Ingredient> objects = new ArrayList<>();
        objects.add(ingredient);
        objects.add(Ingredient.fromStacks(new ItemStack(ForgeRegistries.ITEMS.getValue(controller.getRegistryName()))));
        iIngredients.setInputIngredients(objects);
    }

    @Override
    public void setRecipe(IRecipeLayout iRecipeLayout, MachineStructureRecipe recipe, IIngredients iIngredients) {
        this.xRotation = 0;
        this.yRotation = 0;
        this.yLastMousePosition = 0;
        this.xLastMousePosition = 0;
        this.scaleFactor = 1.2f;
        this.recipe = recipe;
        this.sliceY = 0;
        this.slicingActive = false;

        this.variantIndices.clear();
        this.tickTimer = 0;
    }

    @Override
    public void draw(MachineStructureRecipe recipe, MatrixStack matrixStack, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (xLastMousePosition == 0) {
            xLastMousePosition = mouseX;
        }
        if (yLastMousePosition == 0) {
            yLastMousePosition = mouseY;
        }

        // Do mouse rotations
        if (GLFW.glfwGetMouseButton(mc.getMainWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) != 0) {
            double relMoveX = mouseX - xLastMousePosition;
            double relMoveY = mouseY - yLastMousePosition;
            xRotation += relMoveX;
            yRotation += relMoveY;
        }

        // Calculate distances
        int furthestX = Integer.MAX_VALUE;
        int nearestX = Integer.MIN_VALUE;
        int furthestZ = Integer.MAX_VALUE;
        int nearestZ = Integer.MIN_VALUE;
        int topY = Integer.MIN_VALUE;
        int bottomY = Integer.MAX_VALUE;
        for (MachineStructureRecipeKeyModel part : recipe.getModels().get(0)) {
            furthestX = Math.min(part.getPos().getX(), furthestX);
            nearestX = Math.max(part.getPos().getX(), nearestX);
            furthestZ = Math.min(part.getPos().getZ(), furthestZ);
            nearestZ = Math.max(part.getPos().getZ(), nearestZ);
            topY = Math.max(part.getPos().getY(), topY);
            bottomY = Math.min(part.getPos().getY(), bottomY);
        }

        // Do mouse scroll zoom
        if (GLFW.glfwGetMouseButton(mc.getMainWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != 0) {
            if (scrollLastPos == 0) {
                scrollLastPos = (int) mouseY;
            }
            scaleFactor += (mouseY - yLastMousePosition) * 0.05;
            scaleFactor = Math.max(0.003f, scaleFactor);
        }
        nearestX++;
        nearestZ++;
        float centreX = ((float) nearestX - furthestX) / 2f;
        float centerY = ((float) topY - bottomY) / 2f;
        float centreZ = ((float) nearestZ - furthestZ) / 2f;
        mc.fontRenderer.drawString(matrixStack, recipe.getName(), 2, 2, 0xFFFFFFFF);

        // Get the block parts for the layer
        List<MachineStructureRecipeKeyModel> parts = recipe.getModels().get(0);
        if (slicingActive) {
            parts = parts.stream().filter(x -> x.getPos().getY() == sliceY).collect(Collectors.toList());
        }

        //float tx = 6.5f, ty = -5, tz = 10;
        float tx = 0, ty = 0, tz = 10;
        Vector3f prePos = new Vector3f(tx, ty, tz);
        Vector3f offset = new Vector3f(centreX / 2, centerY / 2, centreZ / 2);

        // Render the block parts
        for (MachineStructureRecipeKeyModel part : parts) {
            variantIndices.putIfAbsent(part.getPos(), 0);
            if (part.getBlock().isEmpty() && part.getTag().isEmpty() && part.getPort() == null) {
                continue;
            }

            BlockPos bp = new BlockPos(-part.getPos().getX(), part.getPos().getY(), -part.getPos().getZ());

            if (!part.getBlock().equals("")) {
                ResourceLocation resourceLocation = new ResourceLocation(part.getBlock());
                Block block = ForgeRegistries.BLOCKS.getValue(resourceLocation);
                if (block != null) {
                    BlockState defaultState = block.getDefaultState();
                    defaultState = with(defaultState, part.getProperties());
                    renderBlock(defaultState, bp, prePos, offset, matrixStack);
                }
            } else if (!part.getTag().equals("")) {
                ResourceLocation resourceLocation = new ResourceLocation(part.getTag());
                ITag<Block> tag = BlockTags.getCollection().getTagByID(resourceLocation);
                Integer index = this.variantIndices.get(part.getPos());

                Block block = tag.getAllElements().get(index);
                if (this.tickTimer == 0) {
                    this.variantIndices.put(part.getPos(), (index+1) % tag.getAllElements().size());
                }

                if (block != null) {
                    BlockState defaultState = block.getDefaultState();
                    defaultState = with(defaultState, part.getProperties());
                    renderBlock(defaultState, bp, prePos, offset, matrixStack);
                }
            } else if (part.getPort() != null) {
                MachineStructurePort port = part.getPort();
                ArrayList<RegistryObject<MachinePortBlock>> ports = port.isInput() ? MMLoader.IPORT_BLOCKS : MMLoader.OPORT_BLOCKS;
                Integer index = this.variantIndices.get(part.getPos());
                String controllerId = port.getControllerId().get(index);
                if (this.tickTimer == 0) {
                    this.variantIndices.put(part.getPos(), (index+1) % port.getControllerId().size());
                }
                String type = port.getType();
                MachinePortBlock block = null;
                for (RegistryObject<MachinePortBlock> regPortBlock : ports) {
                    MachinePortBlock portBlock = regPortBlock.get();
                    if (portBlock.getPortTypeId().equals(RLUtils.toRL(type)) && portBlock.getControllerId().equals(controllerId)) {
                        block = portBlock;
                        break;
                    }
                }
                if (block != null) {
                    BlockState defaultState = block.getDefaultState();
                    defaultState = with(defaultState, part.getProperties());
                    renderBlock(defaultState, bp, prePos, offset, matrixStack);
                }
            }
        }
        // Render the controller block
        if (sliceY == 0) {
            ControllerBlock block = null;
            MachineStructureBlockPos controllerPos = new MachineStructureBlockPos(0, 0, 0);
            this.variantIndices.putIfAbsent(controllerPos, 0);
            Integer index = this.variantIndices.get(controllerPos);
            String controller = recipe.getControllerId().get(index);
            if (this.tickTimer == 0) {
                this.variantIndices.put(controllerPos, (index+1) % recipe.getControllerId().size());
            }
            for (RegistryObject<ControllerBlock> reg : MMLoader.BLOCKS) {
                if (reg.get().getControllerId().equals(controller)) {
                    block = reg.get();
                }
            }
            if (block != null) {
                BlockState defaultState = block.getDefaultState().with(DirectionalBlock.FACING, Direction.NORTH);
                renderBlock(defaultState, new BlockPos(0, 0, 0), prePos, offset, matrixStack);
            }
        }

        // End tick
        xLastMousePosition = mouseX;
        yLastMousePosition = mouseY;
        getButton().draw(matrixStack, 144, 125);
        if (++this.tickTimer % 40 == 0) {
            this.tickTimer = 0;
        }
    }

    @Override
    public boolean handleClick(MachineStructureRecipe recipe, double mouseX, double mouseY, int mouseButton) {
        if (mouseX > 144 && mouseY > 125 && mouseX < 162 && mouseY < 143) {
            startIncrementSlice();
        }
        return false;
    }

    private void startIncrementSlice() {

        int topY = Integer.MIN_VALUE;
        int bottomY = Integer.MAX_VALUE;

        for (MachineStructureRecipeKeyModel part : recipe.getModels().get(0)) {
            topY = Math.max(part.getPos().getY(), topY);
            bottomY = Math.min(part.getPos().getY(), bottomY);
        }

        if (!slicingActive) {
            slicingActive = true;
            sliceY = bottomY;
        } else if (sliceY == topY) {
            slicingActive = false;
            sliceY = 0;
        } else {
            sliceY++;
        }

    }

    private BlockState with(BlockState defaultState, Map<String, String> props) {
        if (props == null) {
            return defaultState;
        }
        for (Map.Entry<String, String> stringStringEntry : props.entrySet()) {
            for (Property<?> property : defaultState.getProperties()) {
                Optional<?> o = property.parseValue(stringStringEntry.getValue());
                if (!o.isPresent()) {
                    return defaultState;
                }
                for (Comparable<?> allowedValue : property.getAllowedValues()) {
                    defaultState = defaultState.cycleValue(property);
                    if (defaultState.get(property).equals(o.get())) {
                        return defaultState;
                    }
                }
            }
        }
        return defaultState;
    }

    private void renderBlock(BlockState defaultState, BlockPos bp, Vector3f prePos, Vector3f offset, MatrixStack ms) {
        new GuiBlockRenderBuilder(defaultState).at(bp)
            .withPrePosition(prePos)
            .withRotation(new Quaternion(new Vector3f(1, 0, 0), 15 + yRotation, true))
            .withRotation(new Quaternion(new Vector3f(0, -1, 0), 225 - xRotation, true))
            .withScale(new Vector3f(scaleFactor, -scaleFactor, scaleFactor))
            .withOffset(offset)
            .finalize(ms);
    }

}
