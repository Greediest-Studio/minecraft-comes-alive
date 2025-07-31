package com.smd.mca.entity;


import com.smd.mca.core.MCA;
import com.smd.mca.core.minecraft.ItemsMCA;
import com.smd.mca.core.minecraft.SoundsMCA;
import com.smd.mca.enums.EnumReaperAttackState;
import com.smd.mca.util.Util;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
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
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;

import java.util.Collection;

public class EntityGrimReaper extends EntityMob {
    private static final DataParameter<Integer> ATTACK_STATE = EntityDataManager.<Integer>createKey(EntityGrimReaper.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> STATE_TRANSITION_COOLDOWN = EntityDataManager.<Integer>createKey(EntityGrimReaper.class, DataSerializers.VARINT);

    private EnumReaperAttackState cachedAttackState = EnumReaperAttackState.IDLE;
    private int controlResistance = 0;
    private static final int MAX_CONTROL_RESISTANCE = 200;
    private static final float MAX_RESISTANCE_FACTOR = 1.0f;
    private int clearEffectsTimer = 60; // 3秒计时器(60 ticks)
    private static final int CLEAR_EFFECTS_INTERVAL = 60; // 3秒间隔
    private int currentBlockDuration = 0;
    private static final DataParameter<Integer> BLOCK_COUNTER = EntityDataManager.createKey(EntityGrimReaper.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> INVINCIBLE_TICKS = EntityDataManager.createKey(EntityGrimReaper.class, DataSerializers.VARINT);

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
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
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
        this.dataManager.register(BLOCK_COUNTER, 0);
        this.dataManager.register(INVINCIBLE_TICKS, 0);
    }

    public EnumReaperAttackState getAttackState() {
        return cachedAttackState;
    }

    public void setAttackState(EnumReaperAttackState state) {

        if (cachedAttackState != state) {
            cachedAttackState = state;
            this.dataManager.set(ATTACK_STATE, state.getId());

            if (state == EnumReaperAttackState.BLOCK) {
                currentBlockDuration = 30; // 初始化格挡时长为1.5秒
            }

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

    @Override
    public void onStruckByLightning(EntityLightningBolt entity) {
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float damage) {

        if (source.getTrueSource() == null || !(source.getTrueSource() instanceof EntityPlayer)) {
            return false;
        }

        if (this.dataManager.get(INVINCIBLE_TICKS) > 0) {
            return false;
        }

        float maxDamage = this.getMaxHealth() * 0.25F;
        if (damage > maxDamage) {
            damage = maxDamage;
        }

        if (source.isMagicDamage() || source.isExplosion()) {
            controlResistance = Math.min(controlResistance + 20, MAX_CONTROL_RESISTANCE);
        }

        if (source.getTrueSource() != null && source.getTrueSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) source.getTrueSource();
            if (player != null && !player.isDead && rand.nextFloat() < 0.20F) {
                Collection<PotionEffect> activeEffects = player.getActivePotionEffects();
                for (PotionEffect effect : activeEffects) {
                    if (effect.getPotion().isBeneficial()) {
                        player.removePotionEffect(effect.getPotion());
                        break;
                    }
                }
            }
        }

        if (source.getTrueSource() instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) source.getTrueSource();
                if (player != null && !player.isDead
                            && !(source.getImmediateSource() instanceof EntityArrow)
                            && rand.nextFloat() < 0.30F) {
                        double distance = this.getDistance(player);

        if (distance > 5.0D) {
            teleportTo(player.posX, player.posY + 1.5D, player.posZ);
            player.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 1));
            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ENDERMEN_STARE, SoundCategory.HOSTILE, 1.0F, 1.0F);
        }}}




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

            int newBlockCount = this.dataManager.get(BLOCK_COUNTER) + 1;
            this.dataManager.set(BLOCK_COUNTER, newBlockCount);

            if (newBlockCount % 3 == 0) {
                this.dataManager.set(INVINCIBLE_TICKS, 20); // 20 ticks = 1秒
            }

            currentBlockDuration = Math.min(currentBlockDuration + 2, 100);

            float healAmount = this.getMaxHealth() * 0.004f;
            this.setHealth(Math.min(this.getHealth() + healAmount, this.getMaxHealth()));

                // 治疗粒子效果
                if (!world.isRemote) {
                    for (int i = 0; i < 5; i++) {
                        world.spawnParticle(EnumParticleTypes.HEART,
                                posX + (rand.nextDouble() - 0.5),
                                posY + 1.5 + rand.nextDouble(),
                                posZ + (rand.nextDouble() - 0.5),
                                0, 0.1, 0);
                    }
                }

            setStateTransitionCooldown(0);
            return false;
        }

        // Randomly portal behind the player who just attacked.
        else if (!world.isRemote && source.getImmediateSource() instanceof EntityPlayer && rand.nextFloat() > 0.30F) {
            EntityPlayer player = (EntityPlayer) source.getImmediateSource();

            double deltaX = this.posX - player.posX;
            double deltaZ = this.posZ - player.posZ;

            teleportTo(player.posX - (deltaX * 2), player.posY + 2, this.posZ - (deltaZ * 2));
        }

        if (source.getImmediateSource() instanceof EntityArrow) {
            EntityArrow arrow = (EntityArrow) source.getImmediateSource();

            if (arrow != null && arrow.shootingEntity instanceof EntityPlayer && getAttackState() != EnumReaperAttackState.REST) {
                EntityPlayer player = (EntityPlayer) arrow.shootingEntity;
                if (player != null && !player.isDead) {
                double newX = player.posX + rand.nextFloat() > 0.50F ? 2 : -2;
                double newZ = player.posZ + rand.nextFloat() > 0.50F ? 2 : -2;

                teleportTo(newX, player.posY, newZ);
            }}

            arrow.setDead();
            return false;
        }

        else if (this.getAttackState() == EnumReaperAttackState.REST) {
            int mobCount = world.getEntitiesWithinAABB(EntityMob.class, this.getEntityBoundingBox().grow(10.0D)).stream()
                    .filter(mob -> mob != this && !mob.isDead)
                    .toArray().length;

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

        float anyDamageHeal = this.getMaxHealth() * 0.001f;
        this.setHealth(Math.min(this.getHealth() + anyDamageHeal, this.getMaxHealth()));

        return true;
    }

    protected void attackEntity(Entity entity, float damage) {
        EntityLivingBase entityToAttack = this.getAttackTarget();
        if (entityToAttack == null || entityToAttack.isDead) return;

        double attackDistance = this.width * 1.5D + entityToAttack.width;
        double distanceSq = this.getDistanceSq(entityToAttack.posX, entityToAttack.getEntityBoundingBox().minY, entityToAttack.posZ);

        if (distanceSq <= attackDistance * attackDistance && getAttackState() == EnumReaperAttackState.PRE) {
            if (getAttackState() == EnumReaperAttackState.BLOCK) {
                int rX = this.getRNG().nextInt(10);
                int rZ = this.getRNG().nextInt(10);
                teleportTo(this.posX + 5 + rX, this.posY, this.posZ + rZ);
            } else {
                float rawDamage = this.world.getDifficulty().getId() * 5.75F;

                if (entity instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) entity;

                    int foodLevel = player.getFoodStats().getFoodLevel();
                    player.getFoodStats().setFoodLevel(Math.max(foodLevel - 2, 0));
                    // 血量低于10%直接秒杀
                    if (player.getHealth() <= player.getMaxHealth() * 0.1F) {
                        player.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
                    } else {
                        // 分段伤害计算
                        float physicalDamage = rawDamage * 0.6F;  // 60%物理伤害
                        float fireDamage = rawDamage * 0.3F;     // 30%火焰伤害
                        float magicDamage = rawDamage * 0.1F;    // 10%魔法伤害

                        player.attackEntityFrom(DamageSource.causeMobDamage(this), physicalDamage);
                        player.attackEntityFrom(DamageSource.IN_FIRE, fireDamage); // 火焰伤害
                        player.attackEntityFrom(DamageSource.MAGIC, magicDamage);  // 魔法伤害
                        player.setFire(40); // 设置2秒着火（40ticks）
                    }
                } else if (entity instanceof EntityLivingBase) {
                    entity.attackEntityFrom(DamageSource.causeMobDamage(this), rawDamage);
                }

                if (entity instanceof EntityLivingBase) {
                    ((EntityLivingBase) entity).addPotionEffect(new PotionEffect(MobEffects.WITHER, this.world.getDifficulty().getId() * 20, 1));
                }

                setAttackState(EnumReaperAttackState.POST);
                setStateTransitionCooldown(10);
            }
        }

        if (getStateTransitionCooldown() == 0 && entityToAttack != null) {
            double trackingDistance = 4.0D;
            double verticalRange = 3.0D;

            // 计算实体与目标的垂直距离
            double yDiff = Math.abs(this.posY - entityToAttack.posY);
            double horizontalDistance = this.getDistance(entityToAttack.posX, this.posY, entityToAttack.posZ);

            if (horizontalDistance <= trackingDistance && yDiff <= verticalRange) {

                if (entityToAttack instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) entityToAttack;

                    if (player.isActiveItemStackBlocking()) {
                        double dX = this.posX - player.posX;
                        double dZ = this.posZ - player.posZ;

                        teleportTo(player.posX - (dX * 2), player.posY + 2, player.posZ - (dZ * 2));

                        if (!world.isRemote && rand.nextFloat() > 0.20F) {
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
                        if (rand.nextFloat() > 0.7F && getAttackState() != EnumReaperAttackState.PRE) {
                            setStateTransitionCooldown(20);
                            setAttackState(EnumReaperAttackState.BLOCK);
                        } else {
                            setAttackState(EnumReaperAttackState.PRE);
                            setStateTransitionCooldown(20);
                        }
                    }
                }
            } else
            {
                setAttackState(EnumReaperAttackState.IDLE);
            }
        }
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

        if (this.dataManager.get(INVINCIBLE_TICKS) > 0) {
            this.dataManager.set(INVINCIBLE_TICKS, this.dataManager.get(INVINCIBLE_TICKS) - 1);

            if (world.isRemote && rand.nextInt(3) == 0) {
                for (int i = 0; i < 2; i++) {
                    world.spawnParticle(EnumParticleTypes.SPELL_INSTANT,
                            posX + (rand.nextDouble() - 0.5) * 1.5,
                            posY + rand.nextDouble() * 2.5,
                            posZ + (rand.nextDouble() - 0.5) * 1.5,
                            0, 0, 0);
                }
            }
        }

        if (!world.isRemote) {
            // 每3秒清除负面效果
            if (--clearEffectsTimer <= 0) {
                clearNegativePotionEffects();
                clearEffectsTimer = CLEAR_EFFECTS_INTERVAL;
            }
        }

        double prevX = this.prevPosX;
        double prevY = this.prevPosY;
        double prevZ = this.prevPosZ;

        super.onUpdate();
        extinguish();

        if (getAttackState() == EnumReaperAttackState.BLOCK) {
            currentBlockDuration--;
            if (currentBlockDuration <= 0) {
                setAttackState(EnumReaperAttackState.IDLE);
            }
        }

        if (!world.isRemote) {

            // 计算实际位移
            double movedX = posX - prevX;
            double movedY = posY - prevY;
            double movedZ = posZ - prevZ;
            double movedDistanceSq = movedX * movedX + movedY * movedY + movedZ * movedZ;

            // 增强检测灵敏度
            boolean isAbnormalMove = movedDistanceSq > (1.5 * 1.5);
            boolean isExternalForce = getAttackState() != EnumReaperAttackState.TELEPORTING;

            if (isAbnormalMove && isExternalForce) {
                // 大幅增加抵抗值
                controlResistance = Math.min(controlResistance + 60, MAX_CONTROL_RESISTANCE);

                // 立即触发强力反击
                EntityLivingBase target = getAttackTarget();
                if (target != null) {
                    teleportTo(target.posX, target.posY + 1.5, target.posZ);
                    // 反击时附加负面效果
                    if (!world.isRemote) {
                        target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 100, 0));
                        target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 2));
                    }
                    world.playSound(null, posX, posY, posZ,
                            SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.HOSTILE, 1.5F, 0.8F);
                }
                // 重置抵抗值并延长效果
                controlResistance = MAX_CONTROL_RESISTANCE;
                clearEffectsTimer += 20; // 延长1秒清除间隔
            }

            // 每tick减少抵抗值
            if (controlResistance > 0) {
                controlResistance--;
            }
        }

        if (!MCA.getConfig().allowGrimReaper) {
            setDead();
        }

        bossInfo.setPercent(this.getHealth() / this.getMaxHealth());

        if (this.getAttackTarget() == null || this.getAttackTarget().isDead) {
            EntityPlayer closestPlayer = this.world.getClosestPlayerToEntity(this, 48.0D);
            if (closestPlayer != null) {
                this.setAttackTarget(closestPlayer);
            }
        }

        EntityLivingBase entityToAttack = this.getAttackTarget();
        if (entityToAttack != null && !entityToAttack.isDead
                && getAttackState() != EnumReaperAttackState.REST) {
            attackEntity(entityToAttack, 5.0F);
            this.getMoveHelper().setMoveTo(entityToAttack.posX, entityToAttack.posY, entityToAttack.posZ, 0.6F); // 提高移动速度

            Vec3d targetVec = new Vec3d(
                    entityToAttack.posX - this.posX,
                    entityToAttack.posY - this.posY,
                    entityToAttack.posZ - this.posZ
            ).normalize();

            double distanceToTarget = this.getDistance(entityToAttack);
            double speedFactor = distanceToTarget < 3.0D ? 0.15D : 0.35D;

            this.motionX = targetVec.x * speedFactor;
            this.motionZ = targetVec.z * speedFactor;
        }

        if (world.isRemote && getAttackState() == EnumReaperAttackState.REST) {
            floatingTicks += 0.1F;
        }
        
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

                int dX = rand.nextInt(8) + 4 * (rand.nextFloat() > 0.50F ? 1 : -1);
                int dZ = rand.nextInt(8) + 4 * (rand.nextFloat() > 0.50F ? 1 : -1);
                int y = Util.getSpawnSafeTopLevel(world, (int) posX + dX, 256, (int) posZ + dZ);

                EntityLightningBolt bolt = new EntityLightningBolt(world, dX, y, dZ, false);
                world.addWeatherEffect(bolt);

                // Also spawn a random skeleton or zombie.
                if (!world.isRemote) {
                    EntityMob mob = rand.nextFloat() > 0.50F ? new EntityZombie(world) : new EntitySkeleton(world);
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
    public void move(MoverType type, double x, double y, double z) {

        if (controlResistance >= MAX_CONTROL_RESISTANCE) {
            return;
        }

        float resistanceFactor = 1.0f - (controlResistance / (float)MAX_CONTROL_RESISTANCE * MAX_RESISTANCE_FACTOR);
        super.move(type, x * resistanceFactor, y * resistanceFactor, z * resistanceFactor);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
    }

    @Override
    public String getName() {
        return I18n.format("entity.boss.grimreaper");
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

            if (!world.isBlockLoaded(new BlockPos(x, y, z))) return;

            EnumReaperAttackState current = getAttackState();
            if (current != EnumReaperAttackState.TELEPORTING) {
                setAttackState(EnumReaperAttackState.TELEPORTING);
            }

            this.setPositionAndUpdate(x, y, z);

            if (rand.nextFloat() < 0.9F) {
                this.playSound(SoundEvents.ENTITY_ENDERMEN_TELEPORT, 1.5F, 1.0F);
            }

            if (current != EnumReaperAttackState.TELEPORTING) {
                setAttackState(current);
            }
        }
    }


    private void clearNegativePotionEffects() {
        Collection<PotionEffect> effects = this.getActivePotionEffects();
        if (effects.isEmpty()) return;

        for (PotionEffect effect : effects) {
            Potion potion = effect.getPotion();
            if (!potion.isBeneficial()) {
                this.removePotionEffect(potion);
            }
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