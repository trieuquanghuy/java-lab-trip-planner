export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  user: UserInfo;
}

export interface SignupRequest {
  email: string;
  password: string;
}

export interface SignupResponse {
  userId: string;
}

export interface RefreshResponse {
  accessToken: string;
  expiresIn: number;
}

export interface UserInfo {
  id: string;
  email: string;
  emailVerified: boolean;
}
