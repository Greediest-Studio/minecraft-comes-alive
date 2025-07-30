package com.smd.mca.entity;


import com.smd.mca.core.MCA;
import com.smd.mca.core.minecraft.ItemsMCA;
import com.smd.mca.core.minecraft.SoundsMCA;
import com.smd.mca.enums.EnumReaperAttackState;
import com.smd.mca.util.Util;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EntityGrimReaper extends EntityMob {
    private static final DataParameter<Integer> ATTACK_STATE = EntityDataManager.<Integer>createKey(EntityGrimReaper.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> STATE_TRANSITION_COOLDOWN = EntityDataManager.<Integer>createKey(EntityGrimReaper.class, DataSerializers.VARINT);

    private final BossInfoServer bossInfo = (BossInfoServer) (new BossInfoServer(this.getDisplayName(), BossInfo.Color.PURPLE, BossInfo.Overlay.PROGRESS)).setDarkenSky(true);
    private EntityAINearestAttackableTarget aiNearestAttackableTarget = new EntityAINearestAttackableTarget(this, EntityPlayer.class, true);
    private int healingCooldown;
    private int timesHealed;

    private float floatingTicks;

    public EntityGrimReaper(World world) {
        super(world);
        setSize(1.0F, 2.6F);
        this.experienceValue = 100;

        this.tasks.addTask(1, new EntityAISwimming(this));
        this.tasks.addTask(4, new EntityAIWander(this, 1.0D));
        this.tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(5, new EntityAILookIdle(this));
        this.tasks.addTask(2, new EntityAIAttackMelee(this, 1.1D, false));
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, false, new Class[0]));
        this.targetTasks.addTask(2, aiNearestAttackableTarget);
    }

    @Override
    protected final void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(50.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.30F);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(4.5F);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(300.0F);
    }

    @Override
    protected void dropFewItems(boolean hitByPlayer, int lootingLvl) {
        dropItem(ItemsMCA.STAFF_OF_LIFE, 1);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(ATTACK_STATE, 0);
        this.dataManager.register(STATE_TRANSITION_COOLDOWN, 0);
    }

    public EnumReaperAttackState getAttackState() {
        return EnumReaperAttackState.fromId(this.dataManager.get(ATTACK_STATE));
    }

    public void setAttackState(EnumReaperAttackState state) {
        // Only update if needed so that sounds only play once.
        if (this.dataManager.get(ATTACK_STATE) != state.getId()) {
            this.dataManager.set(ATTACK_STATE, state.getId());

            switch (state) {
                case PRE:
                    this.playSound(SoundsMCA.reaper_scythe_out, 1.0F, 1.0F);
                    break;
                case POST:
                    this.playSound(SoundsMCA.reaper_scythe_swing, 1.0F, 1.0F);
                    break;
            }
        }
    }

    public boolean hasEntityToAttack() {
        return this.getAttackTarget() != null;
    }

    @Override
    public void onStruckByLightning(EntityLightningBolt entity) {
        return;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float damage) {
        bossInfo.setPercent(this.getHealth() / this.getMaxHealth());

        if (source.getTrueSource() instanceof EntityPlayer && rand.nextFloat() <= 0.20F) {
            EntityPlayer player = (EntityPlayer) source.getTrueSource();
            List<PotionEffect> activeEffects = new ArrayList<>(player.getActivePotionEffects());

            Optional<PotionEffect> positiveEffect = activeEffects.stream()
                    .filter(effect -> effect.getPotion().isBeneficial())
                    .findAny();

            positiveEffect.ifPresent(effect -> {
                player.removePotionEffect(effect.getPotion());
            });
        }

        if (source.getTrueSource() instanceof EntityPlayer
                && !(source.getImmediateSource() instanceof EntityArrow)
                && rand.nextFloat() <= 0.30F) {

            EntityPlayer player = (EntityPlayer) source.getTrueSource();
            double distance = this.getDistance(player);

            if (distance > 5.0D) {
                teleportTo(player.posX, player.posY + 1.5D, player.posZ);
                player.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 1));
                world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ENDERMEN_STARE, SoundCategory.HOSTILE, 1.0F, 1.0F);
            }
        }




        // Ignore wall damage and fire damage.
        if (source == DamageSource.IN_WALL || source == DamageSource.ON_FIRE || source.isExplosion() || source == DamageSource.IN_FIRE) {
            // Teleport out of any walls we may end up in.
            if (source == DamageSource.IN_WALL) {
                teleportTo(this.posX, this.posY + 3, this.posZ);
            }

            return false;
        }

        // Ignore damage when blocking, and teleport behind the player when they attempt to block.
        else if (!world.isRemote && this.getAttackState() == EnumReaperAttackState.BLOCK && source.getImmediateSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) source.getImmediateSource();

            double deltaX = this.posX - player.posX;
            double deltaZ = this.posZ - player.posZ;

            this.playSound(SoundsMCA.reaper_block, 1.0F, 1.0F);
            teleportTo(player.posX - (deltaX * 2), player.posY + 2, this.posZ - (deltaZ * 2));
            if (!world.isRemote) {
                EntityLightningBolt lightning = new EntityLightningBolt(
                        world,
                        player.posX,
                        player.posY,
                        player.posZ,
                        false
                );
                world.addWeatherEffect(lightning);
            }
            setStateTransitionCooldown(0);
            return false;
        }

        // Randomly portal behind the player who just attacked.
        else if (!world.isRemote && source.getImmediateSource() instanceof EntityPlayer && rand.nextFloat() >= 0.30F) {
            EntityPlayer player = (EntityPlayer) source.getImmediateSource();

            double deltaX = this.posX - player.posX;
            double deltaZ = this.posZ - player.posZ;

            teleportTo(player.posX - (deltaX * 2), player.posY + 2, this.posZ - (deltaZ * 2));
        }

        // Teleport behind the player who fired an arrow and ignore its damage.
        else if (source.getImmediateSource() instanceof EntityArrow) {
            EntityArrow arrow = (EntityArrow) source.getImmediateSource();

            if (arrow.shootingEntity instanceof EntityPlayer && getAttackState() != EnumReaperAttackState.REST) {
                EntityPlayer player = (EntityPlayer) arrow.shootingEntity;
                double newX = player.posX + rand.nextFloat() >= 0.50F ? 2 : -2;
                double newZ = player.posZ + rand.nextFloat() >= 0.50F ? 2 : -2;

                teleportTo(newX, player.posY, newZ);
            }

            arrow.setDead();
            return false;
        }

        // Still take damage when healing, but reduced by a third.
        else if (this.getAttackState() == EnumReaperAttackState.REST) {
            // 统计10格范围内的小怪数量（不包括Boss自身）
            int mobCount = world.getEntitiesWithinAABB(EntityMob.class, this.getEntityBoundingBox().grow(10.0D)).stream()
                    .filter(mob -> mob != this && !mob.isDead)
                    .toArray().length;

            // 每个小怪提升5%减伤，最多45%
            float reductionRatio = MathHelper.clamp(mobCount * 0.05F, 0.0F, 0.45F);
            damage *= (0.8F - reductionRatio);
        }

        super.attackEntityFrom(source, damage);

        if (!world.isRemote && this.getHealth() <= (this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getBaseValue() / 2) && healingCooldown == 0) {
            setAttackState(EnumReaperAttackState.REST);
            healingCooldown = 4200; // 3 minutes 30 seconds
            teleportTo(this.posX, this.posY + 8, this.posZ);
            setStateTransitionCooldown(1200); // 1 minute
        }

        return true;
    }

    protected void attackEntity(Entity entity, float damage) {
        EntityLivingBase entityToAttack = this.getAttackTarget();
        if (entityToAttack == null) return;

        // 改进攻击距离计算
        double attackDistance = this.width * 1.5D + entityToAttack.width;
        double distanceSq = this.getDistanceSq(entityToAttack.posX, entityToAttack.getEntityBoundingBox().minY, entityToAttack.posZ);

        // 修复攻击失效问题
        if (distanceSq <= attackDistance * attackDistance && getAttackState() == EnumReaperAttackState.PRE) {
            if (getAttackState() == EnumReaperAttackState.BLOCK) {
                int rX = this.getRNG().nextInt(10);
                int rZ = this.getRNG().nextInt(10);
                teleportTo(this.posX + 5 + rX, this.posY, this.posZ + rZ);
            } else {
                entity.attackEntityFrom(DamageSource.causeMobDamage(this), this.world.getDifficulty().getId() * 5.75F);

                if (entity instanceof EntityLivingBase) {
                    ((EntityLivingBase) entity).addPotionEffect(new PotionEffect(MobEffects.WITHER, this.world.getDifficulty().getId() * 20, 1));
                }

                setAttackState(EnumReaperAttackState.POST);
                setStateTransitionCooldown(10);
            }
        }

        // Check if we're waiting for cooldown from the last attack.
        if (getStateTransitionCooldown() == 0 && entityToAttack != null) {
            double trackingDistance = 4.0D; // 增加追踪范围
            double verticalRange = 3.0D; // 增加垂直追踪范围

            // 计算实体与目标的垂直距离
            double yDiff = Math.abs(this.posY - entityToAttack.posY);
            double horizontalDistance = this.getDistance(entityToAttack.posX, this.posY, entityToAttack.posZ);

            // 增强追踪逻辑
            if (horizontalDistance <= trackingDistance && yDiff <= verticalRange) {
                // Check to see if the player's blocking, then teleport behind them.
                // Also randomly swap their selected item with something else in the hotbar and apply blindness.
                if (entityToAttack instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) entityToAttack;

                    if (player.isActiveItemStackBlocking()) {
                        double dX = this.posX - player.posX;
                        double dZ = this.posZ - player.posZ;

                        teleportTo(player.posX - (dX * 2), player.posY + 2, player.posZ - (dZ * 2));

                        if (!world.isRemote && rand.nextFloat() >= 0.20F) {
                            int currentItem = player.inventory.currentItem;
                            int randomItem = rand.nextInt(InventoryPlayer.getHotbarSize());
                            ItemStack currentItemStack = player.inventory.mainInventory.get(currentItem);
                            ItemStack randomItemStack = player.inventory.mainInventory.get(randomItem);

                            player.inventory.mainInventory.set(currentItem, randomItemStack);
                            player.inventory.mainInventory.set(randomItem, currentItemStack);

                            player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, this.world.getDifficulty().getId() * 40, 1));
                        }
                    } else // If the player is not blocking, ready the scythe, or randomly block their attack.
                    {
                        // 修复格挡条件判断 (原40.0F改为0.4F)
                        if (rand.nextFloat() >= 0.4F && getAttackState() != EnumReaperAttackState.PRE) {
                            setStateTransitionCooldown(20);
                            setAttackState(EnumReaperAttackState.BLOCK);
                        } else {
                            setAttackState(EnumReaperAttackState.PRE);
                            setStateTransitionCooldown(20);
                        }
                    }
                }
            } else // Reset the attacking state when we're more than 3 blocks away.
            {
                setAttackState(EnumReaperAttackState.IDLE);
            }
        }
    }

    protected Entity findPlayerToAttack() {
        return world.getClosestPlayerToEntity(this, 48.0D);
    }

    @Override
    public int getTalkInterval() {
        return 300;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundsMCA.reaper_idle;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundsMCA.reaper_death;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WITHER_HURT;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        extinguish(); // No fire.

        if (!MCA.getConfig().allowGrimReaper) {
            setDead();
        }


        // 增强索敌逻辑：当没有攻击目标时主动寻找
        if (this.getAttackTarget() == null || this.getAttackTarget().isDead) {
            EntityPlayer closestPlayer = this.world.getClosestPlayerToEntity(this, 30.0D);
            if (closestPlayer != null) {
                this.setAttackTarget(closestPlayer);
            }
        }

        EntityLivingBase entityToAttack = this.getAttackTarget();

        if (entityToAttack != null && getAttackState() != EnumReaperAttackState.REST) {
            attackEntity(entityToAttack, 5.0F);
            this.getMoveHelper().setMoveTo(entityToAttack.posX, entityToAttack.posY, entityToAttack.posZ, 0.6F); // 提高移动速度

            // ==== 防止穿过玩家的关键逻辑 ====
            Vec3d targetVec = new Vec3d(
                    entityToAttack.posX - this.posX,
                    entityToAttack.posY - this.posY,
                    entityToAttack.posZ - this.posZ
            ).normalize();

            // 在接近目标时减速防止穿过
            double distanceToTarget = this.getDistance(entityToAttack);
            double speedFactor = distanceToTarget < 3.0D ? 0.15D : 0.35D;

            this.motionX = targetVec.x * speedFactor;
            this.motionZ = targetVec.z * speedFactor;
            // ============================
        }

        // Increment floating ticks on the client when resting.
        if (world.isRemote && getAttackState() == EnumReaperAttackState.REST) {
            floatingTicks += 0.1F;
        }

        // Increase health when resting and check to stop rest state.
        // Runs on common to spawn lightning.
        if (getAttackState() == EnumReaperAttackState.REST) {
            if (!world.isRemote && getStateTransitionCooldown() == 1) {
                setAttackState(EnumReaperAttackState.IDLE);
                timesHealed++;
            } else if (!world.isRemote && getStateTransitionCooldown() % 100 == 0) {
                float healAmount = this.getMaxHealth() * 0.04f;
                this.setHealth(Math.min(this.getHealth() + healAmount, this.getMaxHealth()));

                for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, this.getEntityBoundingBox().grow(6.0D))) {
                    Vec3d direction = new Vec3d(player.posX - this.posX, 0, player.posZ - this.posZ).normalize();
                    double pushPower = 0.6D;

                    player.motionX += direction.x * pushPower;
                    player.motionZ += direction.z * pushPower;

                    world.playSound(null, player.posX, player.posY, player.posZ, SoundsMCA.reaper_summon, SoundCategory.HOSTILE, 1.0F, 0.8F);
                    world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, player.posX, player.posY + 1.0D, player.posZ, 0.0D, 0.1D, 0.0D);
                }

                int dX = rand.nextInt(8) + 4 * (rand.nextFloat() >= 0.50F ? 1 : -1);
                int dZ = rand.nextInt(8) + 4 * (rand.nextFloat() >= 0.50F ? 1 : -1);
                int y = Util.getSpawnSafeTopLevel(world, (int) posX + dX, 256, (int) posZ + dZ);

                EntityLightningBolt bolt = new EntityLightningBolt(world, dX, y, dZ, false);
                world.addWeatherEffect(bolt);

                // Also spawn a random skeleton or zombie.
                if (!world.isRemote) {
                    EntityMob mob = rand.nextFloat() >= 0.50F ? new EntityZombie(world) : new EntitySkeleton(world);
                    mob.setPosition(posX + dX + 4, y, posZ + dZ + 4);

                    if (mob instanceof EntitySkeleton) {
                        mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    }

                    world.spawnEntity(mob);
                }
            }
        }

        if (this.getHealth() <= 0.0F) {
            motionX = 0;
            motionY = 0;
            motionZ = 0;
            return;
        }

        // Stop at our current position if resting
        if (getAttackState() == EnumReaperAttackState.REST) {
            motionX = 0;
            motionY = 0;
            motionZ = 0;
        }

        // Logic for flying.
        fallDistance = 0.0F;

        if (motionY > 0) {
            motionY = motionY * 1.04F;
        } else {
            double yMod = Math.sqrt((motionX * motionX) + (motionZ * motionZ));
            motionY = motionY * 0.6F + yMod * 0.3F;
        }

        // Tick down cooldowns.
        if (getStateTransitionCooldown() > 0) {
            setStateTransitionCooldown(getStateTransitionCooldown() - 1);
        }

        if (healingCooldown > 0) {
            healingCooldown--;
        }

        // See if our entity to attack has died at any point.
        if (entityToAttack != null && entityToAttack.isDead) {
            this.setAttackTarget(null);
            setAttackState(EnumReaperAttackState.IDLE);
        }

        // Move towards target if we're not resting
        if (entityToAttack != null && getAttackState() != EnumReaperAttackState.REST) {
            // If we have a creature to attack, we need to move downwards if we're above it, and vice-versa.
            double sqDistanceTo = Math.sqrt(Math.pow(entityToAttack.posX - posX, 2) + Math.pow(entityToAttack.posZ - posZ, 2));
            float moveAmount = 0.0F;

            if (sqDistanceTo < 8F) {
                moveAmount = MathHelper.clamp(((8F - (float) sqDistanceTo) / 8F) * 4F, 0, 2.5F);
            }

            if (entityToAttack.posY + 0.2F < posY) {
                motionY = motionY - 0.05F * moveAmount;
            }

            if (entityToAttack.posY - 0.5F > posY) {
                motionY = motionY + 0.01F * moveAmount;
            }
        }
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
    }

    @Override
    public String getName() {
        return "Grim Reaper";
    }

    @Override
    protected boolean canDespawn() {
        return true;
    }

    public int getStateTransitionCooldown() {
        return this.dataManager.get(STATE_TRANSITION_COOLDOWN);
    }

    public void setStateTransitionCooldown(int value) {
        this.dataManager.set(STATE_TRANSITION_COOLDOWN, value);
    }

    public float getFloatingTicks() {
        return floatingTicks;
    }

    private void teleportTo(double x, double y, double z) {
        if (!world.isRemote) {
            this.playSound(SoundEvents.ENTITY_ENDERMEN_TELEPORT, 2.0F, 1.0F);
            this.setPosition(x, y, z);
            this.playSound(SoundEvents.ENTITY_ENDERMEN_TELEPORT, 2.0F, 1.0F);
        }
    }

    @Override
    public boolean isNonBoss() {
        return false;
    }

    /**
     * Add the given player to the list of players tracking this entity. For instance, a player may track a boss in
     * order to view its associated boss bar.
     */
    @Override
    public void addTrackingPlayer(EntityPlayerMP player) {
        super.addTrackingPlayer(player);
        this.bossInfo.addPlayer(player);
    }

    /**
     * Removes the given player from the list of players tracking this entity. See {@link Entity#addTrackingPlayer} for
     * more information on tracking.
     */
    @Override
    public void removeTrackingPlayer(EntityPlayerMP player) {
        super.removeTrackingPlayer(player);
        this.bossInfo.removePlayer(player);
    }
}