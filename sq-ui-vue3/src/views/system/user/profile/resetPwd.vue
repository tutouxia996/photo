<template>
   <div>
      <el-alert
        v-if="isForceChange"
        :title="forceChangeMsg"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form ref="pwdRef" :model="user" :rules="rules" label-width="80px">
         <el-form-item label="旧密码" prop="oldPassword">
            <el-input v-model="user.oldPassword" placeholder="请输入旧密码" type="password" show-password />
         </el-form-item>
         <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="user.newPassword" placeholder="请输入新密码" type="password" show-password />
         </el-form-item>
         <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="user.confirmPassword" placeholder="请确认新密码" type="password" show-password/>
         </el-form-item>
         <el-form-item>
         <el-button type="primary" @click="submit">保存</el-button>
         <el-button v-if="!isForceChange" type="danger" @click="close">关闭</el-button>
         </el-form-item>
      </el-form>
   </div>
</template>

<script setup>
import { ElNotification } from 'element-plus'
import { updateUserPwd } from "@/api/system/user";
import useUserStore from '@/store/modules/user'

const { proxy } = getCurrentInstance();
const router = useRouter();
const userStore = useUserStore();

const forceChangeMessages = {
  first_login: '首次登录，请立即修改密码后方可访问系统',
  expired: '密码已过期，请修改密码后方可访问系统'
}
const isForceChange = computed(() => userStore.forceChangePwd)
const forceChangeMsg = computed(() => forceChangeMessages[userStore.forceChangePwdReason] || '请修改密码后方可访问系统')

const user = reactive({
  oldPassword: undefined,
  newPassword: undefined,
  confirmPassword: undefined
});

const equalToPassword = (rule, value, callback) => {
  if (user.newPassword !== value) {
    callback(new Error("两次输入的密码不一致"));
  } else {
    callback();
  }
};

const passwordComplexityRules = [
  { required: true, message: "新密码不能为空", trigger: "blur" },
  { min: 8, max: 20, message: "长度在 8 到 20 个字符", trigger: "blur" },
  { pattern: /[A-Z]/, message: "必须包含大写字母", trigger: "blur" },
  { pattern: /[a-z]/, message: "必须包含小写字母", trigger: "blur" },
  { pattern: /\d/, message: "必须包含数字", trigger: "blur" },
  { pattern: /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/, message: "必须包含至少一个特殊字符", trigger: "blur" },
  { pattern: /^[^<>"'|\\]+$/, message: "不能包含非法字符：< > \" ' \\\ |", trigger: "blur" }
];

const rules = ref({
  oldPassword: [{ required: true, message: "旧密码不能为空", trigger: "blur" }],
  newPassword: passwordComplexityRules,
  confirmPassword: [{ required: true, message: "确认密码不能为空", trigger: "blur" }, { required: true, validator: equalToPassword, trigger: "blur" }]
});

/** 提交按钮 */
function submit() {
  proxy.$refs.pwdRef.validate(valid => {
    if (valid) {
      updateUserPwd(user.oldPassword, user.newPassword).then(response => {
        ElNotification.closeAll();
        proxy.$modal.msgSuccess("修改成功");
        userStore.forceChangePwd = false;
        router.push("/");
      });
    }
  });
};

/** 关闭按钮 */
function close() {
  proxy.$tab.closePage();
};
</script>
